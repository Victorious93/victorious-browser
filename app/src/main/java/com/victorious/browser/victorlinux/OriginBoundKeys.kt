package com.victorious.browser.victorlinux

/** Minimal storage seam so the binding logic is testable without the Android Keystore. */
interface SecretStore {
    fun put(name: String, value: String)
    fun get(name: String): String?
    fun remove(name: String)
}

/**
 * API keys for VictorLinux PCs. Each key is stored together with the origin it was saved for and is only
 * ever returned for that exact origin, so editing a profile's address cannot send the old key elsewhere.
 * Keys are never part of exports, logs, history or URLs.
 */
class OriginBoundKeys(private val store: SecretStore) {
    private fun name(profileId: String) = "victorlinux_key_$profileId"

    fun save(profileId: String, origin: String, key: String) {
        val k = key.trim()
        if (k.isEmpty()) { store.remove(name(profileId)); return }
        require(!k.contains('\n') && !k.contains('\r')) { "invalid key" }
        store.put(name(profileId), "$origin\n$k")
    }

    fun get(profileId: String, origin: String): String? {
        val raw = store.get(name(profileId)) ?: return null
        val i = raw.indexOf('\n')
        if (i < 0 || raw.substring(0, i) != origin) return null
        return raw.substring(i + 1)
    }

    fun has(profileId: String, origin: String) = get(profileId, origin) != null
    fun revoke(profileId: String) = store.remove(name(profileId))
}
