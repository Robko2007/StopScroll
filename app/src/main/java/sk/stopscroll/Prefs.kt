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

    /** Koľko sekúnd sa smie pozerať Reels/Shorts, kým appka zasiahne. */
    var limitSeconds: Int
        get() = sp.getInt("limit_s", 5)
        set(v) = sp.edit().putInt("limit_s", v).apply()

    var blockCount: Int
        get() = sp.getInt("blocks", 0)
        set(v) = sp.edit().putInt("blocks", v).apply()

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
}
