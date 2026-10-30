package com.victorious.browser.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One visited page. Identity is the URL; repeat visits bump [visits] and [lastVisited]. */
data class HistoryEntry(
    val url: String,
    val title: String,
    val lastVisited: Long,
    val visits: Int = 1,
)

/** Pure, Android-free history logic (unit-tested in HistoryLogicTest). */
internal object HistoryLogic {
    const val MAX_ENTRIES = 500

    /** Only real web pages are recorded — not about:blank, data:, javascript:, reader blobs, … */
    fun isRecordable(url: String): Boolean {
        val l = url.trim().lowercase()
        return l.startsWith("http://") || l.startsWith("https://")
    }

    /** Record a visit: newest first, de-duplicated by URL, capped at [max]. */
    fun record(
        list: List<HistoryEntry>,
        url: String,
        title: String,
        now: Long,
        max: Int = MAX_ENTRIES,
    ): List<HistoryEntry> {
        if (!isRecordable(url)) return list
        val prev = list.firstOrNull { it.url == url }
        val entry = HistoryEntry(
            url = url,
            title = title.ifBlank { prev?.title ?: "" },
            lastVisited = now,
            visits = (prev?.visits ?: 0) + 1,
        )
        return (listOf(entry) + list.filterNot { it.url == url }).take(max)
    }

    /** Update the title of an existing entry (titles usually arrive after the load). */
    fun retitle(list: List<HistoryEntry>, url: String, title: String): List<HistoryEntry> {
        if (title.isBlank()) return list
        return list.map { if (it.url == url && it.title != title) it.copy(title = title) else it }
    }

    fun search(list: List<HistoryEntry>, query: String): List<HistoryEntry> {
        val q = query.trim()
        if (q.isEmpty()) return list
        return list.filter { it.url.contains(q, ignoreCase = true) || it.title.contains(q, ignoreCase = true) }
    }

    fun encode(list: List<HistoryEntry>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("url", it.url).put("title", it.title)
                    .put("t", it.lastVisited).put("n", it.visits),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url")
                    if (url.isBlank()) continue
                    add(HistoryEntry(url, o.optString("title"), o.optLong("t"), o.optInt("n", 1).coerceAtLeast(1)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/** Persisted browsing history (SharedPreferences + JSON, same pattern as [BookmarkStore]). */
class HistoryStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _entries = MutableStateFlow(HistoryLogic.decode(prefs.getString(KEY, null)))
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    fun record(url: String, title: String) = set(HistoryLogic.record(_entries.value, url, title, System.currentTimeMillis()))
    fun retitle(url: String, title: String) = set(HistoryLogic.retitle(_entries.value, url, title))
    fun remove(url: String) = set(_entries.value.filterNot { it.url == url })
    fun clear() = set(emptyList())

    private fun set(next: List<HistoryEntry>) {
        if (next == _entries.value) return
        _entries.value = next
        prefs.edit().putString(KEY, HistoryLogic.encode(next)).apply()
    }

    companion object {
        private const val PREFS = "browser_history"
        private const val KEY = "history_json"
        @Volatile private var instance: HistoryStore? = null
        fun get(context: Context): HistoryStore =
            instance ?: synchronized(this) { instance ?: HistoryStore(context).also { instance = it } }
    }
}
