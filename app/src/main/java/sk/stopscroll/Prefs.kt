package sk.stopscroll

import android.content.Context

/** Jednoduché uloženie nastavení a štatistík v telefóne. */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("stopscroll", Context.MODE_PRIVATE)

    var instagramOn: Boolean
        get() = sp.getBoolean("ig_on", true)
        set(v) = sp.edit().putBoolean("ig_on", v).apply()

    var youtubeOn: Boolean
        get() = sp.getBoolean("yt_on", true)
        set(v) = sp.edit().putBoolean("yt_on", v).apply()

    /** Instagram len na správy – mimo správ ťa vráti do schránky. */
    var dmOnly: Boolean
        get() = sp.getBoolean("dm_only", false)
        set(v) = sp.edit().putBoolean("dm_only", v).apply()

    /** Koľko sekúnd sa smie pozerať Reels/Shorts, kým appka zasiahne. */
    var limitSeconds: Int
        get() = sp.getInt("limit_s", 5)
        set(v) = sp.edit().putInt("limit_s", v).apply()

    var blockCount: Int
        get() = sp.getInt("blocks", 0)
        set(v) = sp.edit().putInt("blocks", v).apply()

    var redirectCount: Int
        get() = sp.getInt("redirects", 0)
        set(v) = sp.edit().putInt("redirects", v).apply()

    var lastEvent: String
        get() = sp.getString("last_event", "zatiaľ nič") ?: "zatiaľ nič"
        set(v) = sp.edit().putString("last_event", v).apply()

    /** Režim ladenia – služba si zapisuje ID prvkov na obrazovke. */
    var debugOn: Boolean
        get() = sp.getBoolean("debug_on", false)
        set(v) = sp.edit().putBoolean("debug_on", v).apply()

    var debugDump: String
        get() = sp.getString("debug_dump", "") ?: ""
        set(v) = sp.edit().putString("debug_dump", v).apply()

    // ---- Vlastné ID (doplnia sa k tým zabudovaným, dajú sa meniť priamo v appke) ----

    var extraReelIds: String
        get() = sp.getString("x_reel", "") ?: ""
        set(v) = sp.edit().putString("x_reel", v).apply()

    var extraShortsIds: String
        get() = sp.getString("x_shorts", "") ?: ""
        set(v) = sp.edit().putString("x_shorts", v).apply()

    var extraDmIds: String
        get() = sp.getString("x_dm", "") ?: ""
        set(v) = sp.edit().putString("x_dm", v).apply()

    var extraInboxBtnIds: String
        get() = sp.getString("x_inbox_btn", "") ?: ""
        set(v) = sp.edit().putString("x_inbox_btn", v).apply()

    companion object {
        /** "a, b\nc" → [a, b, c]; odstráni aj prípadný prefix "balik:id/". */
        fun parseIds(raw: String): List<String> =
            raw.split(',', '\n', ' ', ';')
                .map { it.trim().substringAfter(":id/") }
                .filter { it.isNotEmpty() }
    }
}
