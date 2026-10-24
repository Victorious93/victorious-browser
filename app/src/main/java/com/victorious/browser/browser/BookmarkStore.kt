package com.victorious.browser.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A saved page. Identity is the URL (adding an existing URL is a no-op). */
data class Bookmark(val url: String, val title: String)

/**
 * Persisted browser bookmarks. SharedPreferences + JSON, same singleton pattern
 * as [com.victorious.browser.userscript.UserScriptStore] / PanelStore. The pure
 * JSON codec ([encodeBookmarks] / [decodeBookmarks]) is unit-tested; the
 * Context-bound store itself is not (no other `*Store` of this shape is either).
 */
class BookmarkStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _bookmarks = MutableStateFlow(decodeBookmarks(prefs.getString(KEY, null)))
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    fun isBookmarked(url: String): Boolean = _bookmarks.value.any { it.url == url }

    /** Add [url] (no-op if already present or blank). */
    fun add(url: String, title: String) {
        if (url.isBlank() || isBookmarked(url)) return
        _bookmarks.value = _bookmarks.value + Bookmark(url, title.ifBlank { url })
        persist()
    }

    fun remove(url: String) {
        _bookmarks.value = _bookmarks.value.filterNot { it.url == url }
        persist()
    }

    /** Add if absent, remove if present; returns the new bookmarked state. */
    fun toggle(url: String, title: String): Boolean {
        val nowBookmarked = !isBookmarked(url)
        if (nowBookmarked) add(url, title) else remove(url)
        return nowBookmarked
    }

    private fun persist() {
        prefs.edit().putString(KEY, encodeBookmarks(_bookmarks.value)).apply()
    }

    companion object {
        private const val PREFS = "browser_bookmarks"
        private const val KEY = "bookmarks_json"

        @Volatile private var instance: BookmarkStore? = null

        fun get(context: Context): BookmarkStore =
            instance ?: synchronized(this) {
                instance ?: BookmarkStore(context).also { instance = it }
            }
    }
}

internal fun encodeBookmarks(list: List<Bookmark>): String {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title)) }
    return arr.toString()
}

internal fun decodeBookmarks(raw: String?): List<Bookmark> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url")
                if (url.isNotBlank()) add(Bookmark(url, o.optString("title", url)))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
