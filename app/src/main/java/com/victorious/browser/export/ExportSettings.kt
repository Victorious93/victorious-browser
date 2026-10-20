package com.victorious.browser.export

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted fleet-export sink configuration (SharedPreferences), same pattern as
 * [com.victorious.browser.scanner.ScannerConfig].
 *
 * Defaults: endpoint URL and bearer token are EMPTY. Nothing is sent anywhere
 * until the user supplies a target URL and taps send. The exact endpoint/auth
 * contract for HackerAI-Commander / Pentest-Swarm-AI is user-supplied — this app
 * does not ship or assume one.
 */
class ExportSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Config(
        val endpointUrl: String = "",
        val bearerToken: String = "",
    )

    private val _config = MutableStateFlow(
        Config(
            endpointUrl = prefs.getString(KEY_URL, "").orEmpty(),
            bearerToken = prefs.getString(KEY_TOKEN, "").orEmpty(),
        ),
    )
    val config: StateFlow<Config> = _config.asStateFlow()

    fun set(endpointUrl: String, bearerToken: String) {
        val cfg = Config(endpointUrl.trim(), bearerToken.trim())
        prefs.edit()
            .putString(KEY_URL, cfg.endpointUrl)
            .putString(KEY_TOKEN, cfg.bearerToken)
            .apply()
        _config.value = cfg
    }

    companion object {
        private const val PREFS = "fleet_export_config"
        private const val KEY_URL = "endpoint_url"
        private const val KEY_TOKEN = "bearer_token"

        @Volatile private var instance: ExportSettings? = null

        fun get(context: Context): ExportSettings =
            instance ?: synchronized(this) {
                instance ?: ExportSettings(context).also { instance = it }
            }
    }
}
