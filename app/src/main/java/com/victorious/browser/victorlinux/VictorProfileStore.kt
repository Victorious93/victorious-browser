package com.victorious.browser.victorlinux

import android.content.Context
import com.victorious.browser.osint.research.SecretBox
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Saved VictorLinux PCs (no secrets). Keys live in [OriginBoundKeys] on top of the Android Keystore. */
class VictorProfileStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("victorlinux_profiles", Context.MODE_PRIVATE)
    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<VictorProfile>> = _profiles.asStateFlow()
    val keys = OriginBoundKeys(KeystoreSecretStore(SecretBox(context)))

    fun upsert(p: VictorProfile) {
        val cur = _profiles.value
        val old = cur.firstOrNull { it.id == p.id }
        // A changed address drops the pinned PC identity and model memory: it may be a different machine.
        val next = if (old != null && old.origin != p.origin) p.copy(pcId = null, lastModel = null) else p
        _profiles.value = if (old == null) cur + next else cur.map { if (it.id == p.id) next else it }
        persist()
    }

    fun delete(id: String) {
        keys.revoke(id)
        _profiles.value = _profiles.value.filterNot { it.id == id }
        persist()
    }

    fun get(id: String) = _profiles.value.firstOrNull { it.id == id }

    private fun persist() {
        val arr = JSONArray()
        _profiles.value.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("origin", p.origin).put("tunnel", p.tunnel)
                .put("pin", p.pinnedCertSha256 ?: JSONObject.NULL).put("pcId", p.pcId ?: JSONObject.NULL).put("lastModel", p.lastModel ?: JSONObject.NULL))
        }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun load(): List<VictorProfile> = runCatching {
        val arr = JSONArray(prefs.getString("profiles", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val ok = OriginPolicy.parse(o.getString("origin"), o.optBoolean("tunnel", false)) as? OriginPolicy.Result.Ok ?: return@mapNotNull null
            VictorProfile(o.getString("id"), o.optString("name", "VictorLinux PC"), ok.origin,
                o.optString("pin").takeIf { Fingerprint.isValid(it) }, o.optString("pcId").takeIf { it.isNotBlank() && it != "null" },
                o.optString("lastModel").takeIf { it.isNotBlank() && it != "null" }, o.optBoolean("tunnel", false))
        }
    }.getOrDefault(emptyList())

    companion object {
        @Volatile private var instance: VictorProfileStore? = null
        fun get(context: Context): VictorProfileStore = instance ?: synchronized(this) { instance ?: VictorProfileStore(context).also { instance = it } }
    }
}

private class KeystoreSecretStore(private val box: SecretBox) : SecretStore {
    override fun put(name: String, value: String) = box.put(name, value)
    override fun get(name: String): String? = box.get(name)
    override fun remove(name: String) = box.put(name, "")
}
