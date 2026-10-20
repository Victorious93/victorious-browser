package com.victorious.browser.antiphish

import android.content.Context
import com.victorious.browser.export.MediaStoreDownloads
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Local detection report log. Stores host-only URLs (no query, no path secrets,
 * never credentials). Export writes a JSON report to Downloads via
 * [MediaStoreDownloads].
 */
class AntiPhishReportLog private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<ReportEntry>> = _entries.asStateFlow()

    data class ReportEntry(
        val timestampMs: Long,
        val host: String,
        val score: Int,
        val level: String,
        val reasons: List<String>,
    )

    fun record(result: PhishingScorer.Result) {
        if (result.host.isBlank() || result.score <= 0) return
        val entry = ReportEntry(
            timestampMs = System.currentTimeMillis(),
            host = result.host,
            score = result.score,
            level = result.level.name,
            reasons = result.reasons.map { "${it.code}: ${it.detail}" },
        )
        val next = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        _entries.value = next
        persist(next)
    }

    fun clear() {
        _entries.value = emptyList()
        prefs.edit().remove(KEY_LOG).apply()
    }

    /** Export current log as JSON to Downloads. Returns location string or null. */
    fun export(): String? {
        val bytes = toJson(_entries.value).toByteArray(Charsets.UTF_8)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date())
        return MediaStoreDownloads.write(
            appContext,
            displayName = "antiphish-report-$stamp.json",
            mimeType = "application/json",
            bytes = bytes,
            subDir = "VictoriousBrowser",
        )
    }

    private fun persist(list: List<ReportEntry>) {
        prefs.edit().putString(KEY_LOG, toJson(list)).apply()
    }

    private fun load(): List<ReportEntry> {
        val raw = prefs.getString(KEY_LOG, null) ?: return emptyList()
        return runCatching { fromJson(raw) }.getOrElse { emptyList() }
    }

    companion object {
        private const val PREFS = "antiphish_report_log"
        private const val KEY_LOG = "entries_json"
        private const val MAX_ENTRIES = 200

        @Volatile private var instance: AntiPhishReportLog? = null

        fun get(context: Context): AntiPhishReportLog =
            instance ?: synchronized(this) {
                instance ?: AntiPhishReportLog(context).also { instance = it }
            }

        fun toJson(entries: List<ReportEntry>): String {
            val arr = JSONArray()
            for (e in entries) {
                arr.put(
                    JSONObject().apply {
                        put("timestampMs", e.timestampMs)
                        put("host", e.host)
                        put("score", e.score)
                        put("level", e.level)
                        put("reasons", JSONArray(e.reasons))
                    },
                )
            }
            return JSONObject().put("version", 1).put("entries", arr).toString(2)
        }

        fun fromJson(raw: String): List<ReportEntry> {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("entries") ?: return emptyList()
            val out = ArrayList<ReportEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val reasonsArr = o.optJSONArray("reasons") ?: JSONArray()
                val reasons = (0 until reasonsArr.length()).map { reasonsArr.getString(it) }
                out += ReportEntry(
                    timestampMs = o.optLong("timestampMs"),
                    host = o.optString("host"),
                    score = o.optInt("score"),
                    level = o.optString("level"),
                    reasons = reasons,
                )
            }
            return out
        }

        /** Host-only sanitizer for any URL — strips userinfo, path, query, fragment. */
        fun hostOnly(url: String): String =
            FormAnalyzer.hostOf(url)?.let(DomainAnalyzer::normalizeHost).orEmpty()
    }
}
