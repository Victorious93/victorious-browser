package com.victorious.browser.urltools

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted URLhaus (abuse.ch) Auth-Key — SharedPreferences, same pattern as
 * [VirusTotalSettings]. Empty by default; [UrlScanner.checkUrlHaus] is not called
 * when this is blank. abuse.ch requires an Auth-Key on every API call as of this
 * app's testing (confirmed live, not assumed — see [UrlScanner]'s doc); get a free
 * one at https://auth.abuse.ch/. The key is sent only as the `Auth-Key` header on
 * URLhaus's own API — not logged, not included in exports.
 */
class UrlHausSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _authKey = MutableStateFlow(prefs.getString(KEY_AUTH_KEY, "").orEmpty())
    val authKey: StateFlow<String> = _authKey.asStateFlow()

    fun setAuthKey(value: String) {
        prefs.edit().putString(KEY_AUTH_KEY, value).apply()
        _authKey.value = value
    }

    companion object {
        private const val PREFS = "urlhaus_settings"
        private const val KEY_AUTH_KEY = "auth_key"

        @Volatile private var instance: UrlHausSettings? = null

        fun get(context: Context): UrlHausSettings =
            instance ?: synchronized(this) {
                instance ?: UrlHausSettings(context).also { instance = it }
            }
    }
}
