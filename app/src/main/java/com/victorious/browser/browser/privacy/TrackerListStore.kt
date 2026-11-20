package com.victorious.browser.browser.privacy

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Persists and fetches the optional tracker-list update ([TrackerListUpdate]).
 * Off until the user enters an https URL and taps Update. The downloaded file lives in
 * app-private storage; the URL is stored in SharedPreferences. No default URL is shipped.
 */
class TrackerListStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val file get() = File(appContext.filesDir, FILE)

    var url: String
        get() = prefs.getString(K_URL, "") ?: ""
        set(v) { prefs.edit().putString(K_URL, v.trim()).apply() }

    /** Human-readable state for the settings UI. */
    fun status(): String {
        val n = prefs.getInt(K_COUNT, 0)
        return if (n == 0) "Using the bundled list only." else
            "Update v${prefs.getInt(K_VERSION, 0)} active: +$n hosts (fetched ${java.text.DateFormat.getDateTimeInstance().format(prefs.getLong(K_TIME, 0))})."
    }

    /** Apply the saved update (if any) at app start. Failures leave the bundled list in place. */
    fun loadSaved() {
        val f = file
        if (!f.isFile) return
        runCatching { TrackerListUpdate.parse(f.readText()).getOrThrow() }
            .onSuccess { TrackerList.setExtra(it.hosts) }
    }

    /** Remove the update and go back to the bundled list. */
    fun clear() {
        file.delete()
        TrackerList.setExtra(emptyMap())
        prefs.edit().remove(K_COUNT).remove(K_VERSION).remove(K_TIME).apply()
    }

    /** Blocking network call: run off the main thread. */
    fun fetch(): Result<TrackerListUpdate.Parsed> = runCatching {
        val u = url
        require(TrackerListUpdate.isAllowedUrl(u)) { "Enter an https:// URL" }
        val conn = URL(u).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true // JDK never follows https -> http
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            check(code in 200..299) { "HTTP $code" }
            val text = conn.inputStream.use { ins ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val r = ins.read(chunk)
                    if (r < 0) break
                    buf.write(chunk, 0, r)
                    check(buf.size() <= TrackerListUpdate.MAX_BYTES) { "List is larger than ${TrackerListUpdate.MAX_BYTES / 1024} KB" }
                }
                buf.toString(Charsets.UTF_8.name())
            }
            val parsed = TrackerListUpdate.parse(text).getOrThrow()
            val tmp = File(appContext.filesDir, "$FILE.tmp")
            tmp.writeText(text)
            check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) { "Could not save list" }
            TrackerList.setExtra(parsed.hosts)
            prefs.edit().putInt(K_COUNT, parsed.count).putInt(K_VERSION, parsed.version)
                .putLong(K_TIME, System.currentTimeMillis()).apply()
            parsed
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val PREFS = "tracker_list_update"
        private const val FILE = "tracker_list_update.json"
        private const val K_URL = "url"
        private const val K_COUNT = "count"
        private const val K_VERSION = "version"
        private const val K_TIME = "time"
        @Volatile private var instance: TrackerListStore? = null
        fun get(context: Context): TrackerListStore =
            instance ?: synchronized(this) { instance ?: TrackerListStore(context).also { instance = it } }
    }
}
