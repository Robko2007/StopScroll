package sk.stopscroll

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Služba prístupnosti:
 *  - keď je na obrazovke Instagram alebo YouTube, každých 0,5 s skontroluje,
 *    či je otvorený prehrávač Reels / Shorts,
 *  - ak tam si nepretržite dlhšie ako limit (predvolene 5 s),
 *    stlačí "Späť" (odíde z Reels/Shorts) a potom "Domov".
 */
class BlockerService : AccessibilityService() {

    companion object {
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"
        private val TARGETS = setOf(IG, YT)

        /*
         * ID prvkov, podľa ktorých appka spozná prehrávač Reels / Shorts.
         * Instagram a YouTube ich občas pri aktualizácii zmenia – vtedy stačí
         * zapnúť v appke "Režim ladenia", pozrieť si aktuálne ID a doplniť ich sem.
         */
        private val IG_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "clips_swipe_refresh_container",
            "clips_ufi_component",
        )
        private val YT_IDS = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "reel_time_bar",
            "shorts_player_container",
        )

        private const val POLL_MS = 500L
        /** Krátky výpadok detekcie (napr. pri prechode medzi videami) nevynuluje odpočet. */
        private const val GRACE_MS = 1500L
        private const val DEBUG_EVERY_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private var polling = false
    private var watchStart = 0L   // kedy si začal pozerať (0 = nepozeráš)
    private var lastSeen = 0L     // kedy bol prehrávač naposledy viditeľný
    private var lastDebugAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            tick()
            if (polling) handler.postDelayed(this, POLL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        prefs.lastEvent = "${now()} – služba spustená"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg in TARGETS && !polling) {
            polling = true
            handler.post(pollRunnable)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        polling = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ---------------------------------------------------------------------

    private fun tick() {
        val root: AccessibilityNodeInfo? = try { rootInActiveWindow } catch (e: Exception) { null }
        val pkg = root?.packageName?.toString()
        val inTarget = pkg != null && pkg in TARGETS
        val t = SystemClock.elapsedRealtime()

        val enabledForApp = when (pkg) {
            IG -> prefs.instagramOn
            YT -> prefs.youtubeOn
            else -> false
        }
        val onShortVideo = inTarget && enabledForApp && isShortVideoScreen(root!!, pkg!!)

        if (prefs.debugOn && inTarget && t - lastDebugAt > DEBUG_EVERY_MS) {
            lastDebugAt = t
            saveDebugDump(root!!, pkg!!, onShortVideo)
        }

        if (onShortVideo) {
            if (watchStart == 0L) {
                watchStart = t
                prefs.lastEvent = "${now()} – zachytené ${label(pkg!!)}, odpočítavam"
            }
            lastSeen = t
            if (t - watchStart >= prefs.limitSeconds * 1000L) {
                block(pkg!!)
            }
        } else if (watchStart != 0L && t - lastSeen > GRACE_MS) {
            watchStart = 0L   // odišiel si z Reels/Shorts – odpočet sa ruší
        }

        // Ak už nie si v IG/YT a nič neodpočítavame, prestaneme kontrolovať (šetrí batériu).
        if (!inTarget && watchStart == 0L) polling = false
    }

    private fun isShortVideoScreen(root: AccessibilityNodeInfo, pkg: String): Boolean {
        val ids = if (pkg == IG) IG_IDS else YT_IDS
        for (id in ids) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            } catch (e: Exception) {
                emptyList()
            }
            if (nodes.any { it.isVisibleToUser }) return true
        }
        return false
    }

    private fun block(pkg: String) {
        watchStart = 0L
        lastSeen = 0L

        // 1) "Späť" – odíde z Reels/Shorts, aby si sa po návrate neobjavil znova v nich
        performGlobalAction(GLOBAL_ACTION_BACK)
        // 2) "Domov" – zavrie appku (presunie ju do pozadia)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 250)

        prefs.blockCount = prefs.blockCount + 1
        prefs.lastEvent = "${now()} – zablokované ${label(pkg)}"
        Toast.makeText(
            this,
            "⛔ ${label(pkg)} zablokované (limit ${prefs.limitSeconds} s)",
            Toast.LENGTH_LONG
        ).show()
    }

    // ---------------------------------------------------------------------
    // Režim ladenia: zapíše, aké ID prvkov sú práve na obrazovke.

    private fun saveDebugDump(root: AccessibilityNodeInfo, pkg: String, detected: Boolean) {
        val ids = sortedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            val id = node.viewIdResourceName
            if (id != null && node.isVisibleToUser) ids.add(id.substringAfter(":id/"))
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        prefs.debugDump = buildString {
            append("${now()} | ${if (pkg == IG) "Instagram" else "YouTube"} | Reels/Shorts detekované: ")
            append(if (detected) "ÁNO" else "nie")
            append("\n\n")
            append(ids.joinToString(", "))
        }
    }

    private fun label(pkg: String) = if (pkg == IG) "Instagram Reels" else "YouTube Shorts"

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
