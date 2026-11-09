package com.victorious.browser.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Address-bar search engines. [queryPrefix] gets the URL-encoded query appended. */
enum class SearchEngine(val label: String, val queryPrefix: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave Search", "https://search.brave.com/search?q="),
    STARTPAGE("Startpage", "https://www.startpage.com/do/search?q="),
    ;

    companion object {
        val DEFAULT = DUCKDUCKGO
        fun fromName(name: String?): SearchEngine = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Persisted browser chrome preferences (currently: the search engine). */
class BrowserPrefs private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
    private val _engine = MutableStateFlow(SearchEngine.fromName(prefs.getString(KEY_ENGINE, null)))
    val searchEngine: StateFlow<SearchEngine> = _engine.asStateFlow()

    private val _textZoom = MutableStateFlow(TextZoom.snap(prefs.getInt(KEY_TEXT_ZOOM, TextZoom.DEFAULT)))
    /** Page text-zoom percent (snapped to a valid [TextZoom] level). */
    val textZoom: StateFlow<Int> = _textZoom.asStateFlow()

    fun setSearchEngine(engine: SearchEngine) {
        _engine.value = engine
        prefs.edit().putString(KEY_ENGINE, engine.name).apply()
    }

    /** Persist a page text-zoom percent; the value is snapped to a valid level first. */
    fun setTextZoom(percent: Int) {
        val snapped = TextZoom.snap(percent)
        _textZoom.value = snapped
        prefs.edit().putInt(KEY_TEXT_ZOOM, snapped).apply()
    }

    companion object {
        private const val KEY_ENGINE = "search_engine"
        private const val KEY_TEXT_ZOOM = "text_zoom"
        @Volatile private var instance: BrowserPrefs? = null
        fun get(context: Context): BrowserPrefs =
            instance ?: synchronized(this) { instance ?: BrowserPrefs(context).also { instance = it } }
    }
}
