package com.victorious.browser.headscale

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted Headscale control-server settings (server URL + API key), same
 * SharedPreferences pattern as [com.victorious.browser.urltools.VirusTotalSettings].
 * The API key is sent only to this configured server's own `/api/v1/...` endpoints.
 */
class HeadscaleSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, "").orEmpty())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "").orEmpty())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun setServerUrl(value: String) {
        prefs.edit().putString(KEY_SERVER_URL, value).apply()
        _serverUrl.value = value
    }

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
        _apiKey.value = value
    }

    companion object {
        private const val PREFS = "headscale_settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"

        @Volatile private var instance: HeadscaleSettings? = null

        fun get(context: Context): HeadscaleSettings =
            instance ?: synchronized(this) {
                instance ?: HeadscaleSettings(context).also { instance = it }
            }
    }
}
