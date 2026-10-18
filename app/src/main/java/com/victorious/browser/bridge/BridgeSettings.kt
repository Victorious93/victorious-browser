package com.victorious.browser.bridge

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Persisted bridge settings: external-control switch (OFF by default) and node-id.
 * Node-id matches ^browser-[0-9a-f]{32}$ and is generated on first run.
 */
class BridgeSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _externalControlEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val externalControlEnabled: StateFlow<Boolean> = _externalControlEnabled.asStateFlow()

    val nodeId: String = prefs.getString(KEY_NODE_ID, null) ?: generateAndPersistNodeId()

    fun isExternalControlEnabled(): Boolean = _externalControlEnabled.value

    fun setExternalControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _externalControlEnabled.value = enabled
    }

    private fun generateAndPersistNodeId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        val id = "browser-$hex"
        prefs.edit().putString(KEY_NODE_ID, id).apply()
        return id
    }

    companion object {
        private const val PREFS = "fleet_bridge"
        private const val KEY_ENABLED = "external_control_enabled"
        private const val KEY_NODE_ID = "node_id"
        private val NODE_ID_REGEX = Regex("^browser-[0-9a-f]{32}$")

        @Volatile private var instance: BridgeSettings? = null

        fun get(context: Context): BridgeSettings =
            instance ?: synchronized(this) {
                instance ?: BridgeSettings(context).also { instance = it }
            }

        fun isValidNodeId(id: String): Boolean = NODE_ID_REGEX.matches(id)
    }
}
