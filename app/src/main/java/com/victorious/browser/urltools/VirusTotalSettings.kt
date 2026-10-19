package com.victorious.browser.urltools

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted VirusTotal API key (SharedPreferences, same pattern as [com.victorious.browser.bridge.BridgeSettings]).
 * Empty by default — [UrlScanner.checkVirusTotal] is simply not called when this is blank.
 * The key never leaves this device except as the `x-apikey` header on VirusTotal's
 * own API calls (see [UrlScanner]); it is not logged and not included in exports.
 */
class VirusTotalSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
        _apiKey.value = value
    }

    companion object {
        private const val PREFS = "virustotal_settings"
        private const val KEY_API_KEY = "api_key"

        @Volatile private var instance: VirusTotalSettings? = null

        fun get(context: Context): VirusTotalSettings =
            instance ?: synchronized(this) {
                instance ?: VirusTotalSettings(context).also { instance = it }
            }
    }
}
