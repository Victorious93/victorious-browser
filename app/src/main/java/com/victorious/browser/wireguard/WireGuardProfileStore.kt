package com.victorious.browser.wireguard

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local store for WireGuard profiles. SharedPreferences + JSON, same pattern as
 * [com.victorious.browser.panels.PanelStore].
 *
 * **This includes each profile's private key, in plaintext SharedPreferences** —
 * the same storage mechanism (and the same honest caveat) as this app's other
 * secrets (`VirusTotalSettings`, `UrlHausSettings`, `BridgeSettings`'s node-id),
 * but a WireGuard private key is materially more sensitive than an API key: it is
 * the device's cryptographic identity on the mesh. This is not hardware-backed or
 * encrypted storage. If this profile store is ever exposed to a threat model worse
 * than "another app on this same device reading this app's private SharedPreferences
 * file" (which already requires a rooted device or a backup-extraction attack),
 * replace this with `androidx.security.crypto`'s `EncryptedSharedPreferences`.
 */
class WireGuardProfileStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<WireGuardProfileFields>> = _profiles.asStateFlow()

    fun add(profile: WireGuardProfileFields) {
        _profiles.value = _profiles.value + profile
        persist()
    }

    fun update(oldName: String, profile: WireGuardProfileFields) {
        _profiles.value = _profiles.value.map { if (it.name == oldName) profile else it }
        persist()
    }

    fun delete(name: String) {
        _profiles.value = _profiles.value.filterNot { it.name == name }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _profiles.value.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<WireGuardProfileFields> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) add(fromJson(arr.getJSONObject(i)))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS = "wireguard_profiles"
        private const val KEY = "profiles_json"

        @Volatile private var instance: WireGuardProfileStore? = null

        fun get(context: Context): WireGuardProfileStore =
            instance ?: synchronized(this) {
                instance ?: WireGuardProfileStore(context).also { instance = it }
            }

        fun newProfileNamePlaceholder(): String = "profile-${UUID.randomUUID().toString().take(8)}"

        private fun toJson(p: WireGuardProfileFields): JSONObject = JSONObject()
            .put("name", p.name)
            .put("privateKeyBase64", p.privateKeyBase64)
            .put("addresses", JSONArray(p.addresses))
            .put("dnsServers", JSONArray(p.dnsServers))
            .put("listenPort", p.listenPort ?: JSONObject.NULL)
            .put("peerPublicKeyBase64", p.peerPublicKeyBase64)
            .put("presharedKeyBase64", p.presharedKeyBase64 ?: JSONObject.NULL)
            .put("endpoint", p.endpoint)
            .put("allowedIps", JSONArray(p.allowedIps))
            .put("persistentKeepaliveSeconds", p.persistentKeepaliveSeconds ?: JSONObject.NULL)

        private fun fromJson(o: JSONObject): WireGuardProfileFields = WireGuardProfileFields(
            name = o.getString("name"),
            privateKeyBase64 = o.getString("privateKeyBase64"),
            addresses = o.getJSONArray("addresses").toStringList(),
            dnsServers = o.optJSONArray("dnsServers")?.toStringList().orEmpty(),
            listenPort = o.optInt("listenPort", -1).takeIf { it > 0 },
            peerPublicKeyBase64 = o.getString("peerPublicKeyBase64"),
            presharedKeyBase64 = o.optString("presharedKeyBase64").takeIf { it.isNotBlank() },
            endpoint = o.getString("endpoint"),
            allowedIps = o.getJSONArray("allowedIps").toStringList(),
            persistentKeepaliveSeconds = o.optInt("persistentKeepaliveSeconds", -1).takeIf { it > 0 },
        )

        private fun JSONArray.toStringList(): List<String> = buildList {
            for (i in 0 until this@toStringList.length()) add(this@toStringList.getString(i))
        }
    }
}
