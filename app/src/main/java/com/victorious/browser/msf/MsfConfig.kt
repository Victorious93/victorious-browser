package com.victorious.browser.msf

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted Metasploit RPC connection config (SharedPreferences).
 *
 * OFF/empty by default: host/user/pass blank, so nothing connects until the user
 * enters credentials and taps Connect. The password is stored only to populate
 * the login form and is never exported.
 */
class MsfConfig private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Config(
        val host: String = "",
        val port: Int = 55553,
        val ssl: Boolean = true,
        val username: String = "",
        val password: String = "",
    )

    private val _config = MutableStateFlow(
        Config(
            host = prefs.getString(KEY_HOST, "").orEmpty(),
            port = prefs.getInt(KEY_PORT, 55553),
            ssl = prefs.getBoolean(KEY_SSL, true),
            username = prefs.getString(KEY_USER, "").orEmpty(),
            password = prefs.getString(KEY_PASS, "").orEmpty(),
        ),
    )
    val config: StateFlow<Config> = _config.asStateFlow()

    fun set(host: String, port: Int, ssl: Boolean, username: String, password: String) {
        val cfg = Config(host.trim(), port, ssl, username.trim(), password)
        prefs.edit()
            .putString(KEY_HOST, cfg.host)
            .putInt(KEY_PORT, cfg.port)
            .putBoolean(KEY_SSL, cfg.ssl)
            .putString(KEY_USER, cfg.username)
            .putString(KEY_PASS, cfg.password)
            .apply()
        _config.value = cfg
    }

    companion object {
        private const val PREFS = "msf_rpc_config"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_SSL = "ssl"
        private const val KEY_USER = "user"
        private const val KEY_PASS = "pass"

        @Volatile private var instance: MsfConfig? = null

        fun get(context: Context): MsfConfig =
            instance ?: synchronized(this) {
                instance ?: MsfConfig(context).also { instance = it }
            }
    }
}
