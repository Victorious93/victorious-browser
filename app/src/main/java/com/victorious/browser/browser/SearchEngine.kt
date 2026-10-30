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

    fun setSearchEngine(engine: SearchEngine) {
        _engine.value = engine
        prefs.edit().putString(KEY_ENGINE, engine.name).apply()
    }

    companion object {
        private const val KEY_ENGINE = "search_engine"
        @Volatile private var instance: BrowserPrefs? = null
        fun get(context: Context): BrowserPrefs =
            instance ?: synchronized(this) { instance ?: BrowserPrefs(context).also { instance = it } }
    }
}
