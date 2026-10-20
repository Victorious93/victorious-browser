package com.victorious.browser.webext

/**
 * Pure, in-memory extension registry with all install/enable/delete/value rules.
 * [ExtensionStore] wraps it with on-disk persistence; tests exercise it directly.
 */
class ExtensionRegistry(
    initial: ExtensionCodec.Decoded = ExtensionCodec.Decoded(emptyList(), emptyMap()),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    var extensions: List<Extension> = initial.extensions
        private set
    private val values: MutableMap<String, MutableMap<String, String>> =
        initial.values.mapValues { it.value.toMutableMap() }.toMutableMap()
    var pausedHosts: Set<String> = emptySet()

    sealed class InstallResult {
        data class Installed(val extension: Extension, val warnings: List<String>, val replaced: Boolean) : InstallResult()
        data class Rejected(val reason: String) : InstallResult()
    }

    /**
     * Install from source text. Same name+namespace replaces in place (keeps id,
     * enabled flag, createdAt and stored values) — that is an update, not a duplicate.
     */
    fun install(source: String, bundled: Boolean = false): InstallResult {
        val bytes = source.toByteArray(Charsets.UTF_8).size
        if (bytes > MAX_SOURCE_BYTES) {
            return InstallResult.Rejected("Script is ${bytes / 1024} KB; cap is ${MAX_SOURCE_BYTES / 1024} KB")
        }
        return when (val r = UserscriptParser.parse(source)) {
            is ParseResult.Error -> InstallResult.Rejected(r.message)
            is ParseResult.Ok -> {
                val existing = extensions.firstOrNull { it.name == r.meta.name && it.namespace == r.meta.namespace }
                if (existing == null && extensions.size >= MAX_EXTENSIONS) {
                    return InstallResult.Rejected("Too many extensions installed (max $MAX_EXTENSIONS)")
                }
                val ext = Extension.fromMeta(
                    id = existing?.id ?: newId(), meta = r.meta, source = source,
                    createdAt = existing?.createdAt ?: clock(), bundled = bundled || existing?.bundled == true,
                ).copy(enabled = existing?.enabled ?: true)
                extensions = if (existing != null) extensions.map { if (it.id == ext.id) ext else it } else extensions + ext
                InstallResult.Installed(ext, r.warnings, existing != null)
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        if (extensions.none { it.id == id }) return false
        extensions = extensions.map { if (it.id == id) it.copy(enabled = enabled) else it }
        return true
    }

    fun delete(id: String): Boolean {
        val before = extensions.size
        extensions = extensions.filterNot { it.id == id }
        values.remove(id)
        return extensions.size != before
    }

    fun get(id: String): Extension? = extensions.firstOrNull { it.id == id }

    /** Enabled extensions for [url] at [runAt], unless the host is paused. */
    fun forPage(url: String?, runAt: RunAt): List<Extension> {
        val host = url?.let { UrlParts.parse(it)?.host }
        if (host != null && host in pausedHosts) return emptyList()
        return extensions.filter { it.runAt == runAt && it.appliesTo(url) }
    }

    private fun granted(id: String, grant: String): Boolean =
        get(id)?.let { it.enabled && grant in it.grants } == true

    fun getValue(id: String, key: String): String? =
        if (granted(id, "GM_getValue")) values[id]?.get(key) else null

    fun setValue(id: String, key: String, value: String): Boolean {
        if (!granted(id, "GM_setValue")) return false
        if (key.length > MAX_KEY_CHARS || value.length > MAX_VALUE_CHARS) return false
        val m = values.getOrPut(id) { mutableMapOf() }
        if (key !in m && m.size >= MAX_KEYS) return false
        m[key] = value
        return true
    }

    fun deleteValue(id: String, key: String): Boolean =
        granted(id, "GM_deleteValue") && values[id]?.remove(key) != null

    fun listValues(id: String): List<String> =
        if (granted(id, "GM_listValues")) values[id]?.keys?.sorted().orEmpty() else emptyList()

    fun snapshotValues(): Map<String, Map<String, String>> = values.mapValues { it.value.toMap() }

    companion object {
        const val MAX_SOURCE_BYTES = 2 * 1024 * 1024
        const val MAX_EXTENSIONS = 100
        const val MAX_KEYS = 256
        const val MAX_KEY_CHARS = 256
        const val MAX_VALUE_CHARS = 64 * 1024
    }
}
