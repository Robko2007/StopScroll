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
 * Služba prístupnosti StopScroll.
 *
 * 1) Reels / Shorts: keď si v prehrávači Reels alebo Shorts nepretržite dlhšie
 *    ako limit, stlačí "Späť" a "Domov".
 *
 * 2) Instagram len na správy: keď sa ocitneš na feede, vo vyhľadávaní, v Reels
 *    alebo na profiloch (obrazovky so spodným panelom), vráti ťa do správ.
 *    Obrazovky bez spodného panela (fotoaparát, galéria, hovor...) nechá na pokoji,
 *    aby si mohol posielať fotky a volať.
 */
class BlockerService : AccessibilityService() {

    companion object {
        const val IG = "com.instagram.android"
        const val YT = "com.google.android.youtube"
        private val TARGETS = setOf(IG, YT)

        /*
         * Zabudované ID prvkov. Instagram a YouTube ich občas pri aktualizácii
         * zmenia – nové sa dajú doplniť priamo v appke (sekcia "Vlastné ID").
         */
        private val IG_REEL_IDS = listOf(
            "clips_viewer_view_pager",
            "clips_viewer_container",
            "clips_video_container",
            "clips_swipe_refresh_container",
            "clips_ufi_component",
        )
        private val YT_SHORTS_IDS = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "reel_time_bar",
            "shorts_player_container",
        )
        /** Obrazovky správ (zoznam konverzácií aj samotný chat). */
        private val IG_DM_IDS = listOf(
            "inbox_refreshable_thread_list_recyclerview",
            "direct_inbox_container",
            "direct_inbox_action_bar",
            "direct_thread_container",
            "row_thread_composer_edittext",
            "row_thread_composer_container",
            "direct_thread_fragment_container",
            "message_list",
        )
        /** Tlačidlo, ktoré otvorí správy (lietadielko hore alebo karta dole). */
        private val IG_INBOX_BTN_IDS = listOf(
            "action_bar_inbox_button",
            "direct_tab",
            "inbox_tab",
        )
        /** Popisy tlačidla správ – záloha, ak ID nesedí. */
        private val IG_INBOX_DESCS = listOf(
            "Messenger", "Direct", "Messages", "Správy", "Správa", "Message",
        )
        /** Spodný panel Instagramu = "prehliadacie" obrazovky (feed, hľadanie, reels, profil). */
        private val IG_BROWSE_IDS = listOf(
            "tab_bar",
            "feed_tab",
            "search_tab",
            "clips_tab",
            "profile_tab",
        )

        private const val POLL_MS = 500L
        /** Krátky výpadok detekcie Reels nevynuluje odpočet. */
        private const val GRACE_MS = 1500L
        /** Ako dlho môžeš byť mimo správ, kým ťa vráti (krátke prebliknutie nevadí). */
        private const val DM_GRACE_MS = 700L
        /** Pauza medzi dvoma zásahmi, aby appka neklikala ako zbesilá. */
        private const val ACTION_COOLDOWN_MS = 900L
        /** Poistka proti zacykleniu: veľa zásahov za krátky čas = pauza. */
        private const val LOOP_WINDOW_MS = 20_000L
        private const val LOOP_MAX_ACTIONS = 8
        private const val LOOP_PAUSE_MS = 60_000L
        private const val DEBUG_EVERY_MS = 2000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: Prefs

    private var polling = false
    // Reels / Shorts
    private var watchStart = 0L
    private var lastSeen = 0L
    // Len správy
    private var offDmSince = 0L
    private var lastActionAt = 0L
    private var feedTabAttempts = 0
    private val recentActions = ArrayDeque<Long>()
    private var dmPausedUntil = 0L
    private var dmScreenState = "-"
    // Ladenie
    private var lastDebugAt = 0L

    private val pollRunnable = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Exception) { /* nikdy nespadni */ }
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
        val t = SystemClock.elapsedRealtime()

        var onShortVideo = false
        if (root != null && pkg != null && pkg in TARGETS) {
            val shortsOn = if (pkg == IG) prefs.instagramOn else prefs.youtubeOn
            onShortVideo = shortsOn && anyVisible(root, pkg, shortIds(pkg))

            // Karta Reels (so spodným panelom) → hneď do správ.
            // Reel otvorený zo správ (bez panelu) → platí bežný 5 s limit.
            if (pkg == IG && prefs.dmOnly) {
                handleDmOnly(root, t)
            } else {
                offDmSince = 0L
            }

            if (prefs.debugOn && t - lastDebugAt > DEBUG_EVERY_MS) {
                lastDebugAt = t
                saveDebugDump(root, pkg, onShortVideo)
            }
        } else {
            offDmSince = 0L
        }

        // --- Reels / Shorts odpočet ---
        if (onShortVideo) {
            if (watchStart == 0L) {
                watchStart = t
                prefs.lastEvent = "${now()} – zachytené ${label(pkg!!)}, odpočítavam"
            }
            lastSeen = t
            if (t - watchStart >= prefs.limitSeconds * 1000L) block(pkg!!)
        } else if (watchStart != 0L && t - lastSeen > GRACE_MS) {
            watchStart = 0L
        }

        // Mimo IG/YT a nič neodpočítavame → prestaneme kontrolovať (šetrí batériu).
        val inTarget = pkg != null && pkg in TARGETS
        if (!inTarget && watchStart == 0L) polling = false
    }

    // ---------------------------------------------------------------------
    // Reels / Shorts

    private fun shortIds(pkg: String): List<String> =
        if (pkg == IG) IG_REEL_IDS + Prefs.parseIds(prefs.extraReelIds)
        else YT_SHORTS_IDS + Prefs.parseIds(prefs.extraShortsIds)

    private fun block(pkg: String) {
        watchStart = 0L
        lastSeen = 0L
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 250)

        prefs.blockCount = prefs.blockCount + 1
        prefs.lastEvent = "${now()} – zablokované ${label(pkg)}"
        toast("⛔ ${label(pkg)} zablokované (limit ${prefs.limitSeconds} s)")
    }

    // ---------------------------------------------------------------------
    // Instagram len na správy

    private fun handleDmOnly(root: AccessibilityNodeInfo, t: Long) {
        if (t < dmPausedUntil) { dmScreenState = "pozastavené"; return }

        val inDm = anyVisible(root, IG, IG_DM_IDS + Prefs.parseIds(prefs.extraDmIds))
        val browsing = !inDm && anyVisible(root, IG, IG_BROWSE_IDS)

        dmScreenState = when {
            inDm -> "správy ✅"
            browsing -> "mimo správ ⛔"
            else -> "iná obrazovka (povolená)"
        }

        if (inDm) feedTabAttempts = 0
        if (!browsing) { offDmSince = 0L; return }

        if (offDmSince == 0L) { offDmSince = t; return }
        if (t - offDmSince < DM_GRACE_MS) return
        if (t - lastActionAt < ACTION_COOLDOWN_MS) return

        // Poistka proti zacykleniu (napr. keď Instagram zmení ID obrazovky správ)
        while (recentActions.isNotEmpty() && t - recentActions.first() > LOOP_WINDOW_MS) {
            recentActions.removeFirst()
        }
        if (recentActions.size >= LOOP_MAX_ACTIONS) {
            recentActions.clear()
            dmPausedUntil = t + LOOP_PAUSE_MS
            prefs.lastEvent = "${now()} – režim len správy sa zacyklil, pauza 1 min. Skontroluj ID v Režime ladenia."
            toast("StopScroll: neviem spoľahlivo rozpoznať správy – pauza 1 min")
            return
        }

        lastActionAt = t
        recentActions.addLast(t)
        goToInbox(root)
    }

    private fun goToInbox(root: AccessibilityNodeInfo) {
        // 1) Tlačidlo správ podľa ID alebo podľa popisu
        val btnIds = IG_INBOX_BTN_IDS + Prefs.parseIds(prefs.extraInboxBtnIds)
        if (clickFirstById(root, IG, btnIds) || clickByDescription(root, IG_INBOX_DESCS)) {
            countRedirect("otvorené správy")
            return
        }
        // 2) Nie sme na domovskej karte → prepni na ňu (tam je lietadielko)
        if (feedTabAttempts < 2 && clickFirstById(root, IG, listOf("feed_tab"))) {
            feedTabAttempts++
            return
        }
        // 3) Nevieme nájsť správy → krok späť (v krajnom prípade zavrie Instagram)
        feedTabAttempts = 0
        performGlobalAction(GLOBAL_ACTION_BACK)
        countRedirect("krok späť (tlačidlo správ nenájdené)")
    }

    private fun countRedirect(what: String) {
        prefs.redirectCount = prefs.redirectCount + 1
        prefs.lastEvent = "${now()} – len správy: $what"
    }

    // ---------------------------------------------------------------------
    // Pomocné funkcie

    private fun anyVisible(root: AccessibilityNodeInfo, pkg: String, ids: List<String>): Boolean {
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

    private fun clickFirstById(root: AccessibilityNodeInfo, pkg: String, ids: List<String>): Boolean {
        for (id in ids) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId("$pkg:id/$id")
            } catch (e: Exception) {
                emptyList()
            }
            for (n in nodes) {
                if (n.isVisibleToUser && clickNodeOrParent(n)) return true
            }
        }
        return false
    }

    private fun clickByDescription(root: AccessibilityNodeInfo, descs: List<String>): Boolean {
        for (d in descs) {
            val nodes = try { root.findAccessibilityNodeInfosByText(d) } catch (e: Exception) { emptyList() }
            for (n in nodes) {
                val desc = n.contentDescription?.toString()?.trim() ?: continue
                if (n.isVisibleToUser && desc.equals(d, ignoreCase = true) && clickNodeOrParent(n)) {
                    return true
                }
            }
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = node
        var depth = 0
        while (cur != null && depth < 5) {
            if (cur.isClickable) return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            cur = cur.parent
            depth++
        }
        return false
    }

    // ---------------------------------------------------------------------
    // Režim ladenia: zapíše, čo je práve na obrazovke.

    private fun saveDebugDump(root: AccessibilityNodeInfo, pkg: String, shortDetected: Boolean) {
        val ids = sortedSetOf<String>()
        val descs = sortedSetOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < 800) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser) {
                node.viewIdResourceName?.let { ids.add(it.substringAfter(":id/")) }
                if (node.isClickable && descs.size < 40) {
                    node.contentDescription?.toString()?.trim()
                        ?.takeIf { it.isNotEmpty() && it.length < 40 }
                        ?.let { descs.add(it) }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        prefs.debugDump = buildString {
            append(now()).append(" | ").append(if (pkg == IG) "Instagram" else "YouTube").append('\n')
            append("Reels/Shorts detekované: ").append(if (shortDetected) "ÁNO" else "nie").append('\n')
            if (pkg == IG && prefs.dmOnly) append("Režim len správy: ").append(dmScreenState).append('\n')
            append("\nID:\n").append(ids.joinToString(", "))
            append("\n\nTlačidlá (popisy):\n").append(descs.joinToString(" | "))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun label(pkg: String) = if (pkg == IG) "Instagram Reels" else "YouTube Shorts"

    private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}
