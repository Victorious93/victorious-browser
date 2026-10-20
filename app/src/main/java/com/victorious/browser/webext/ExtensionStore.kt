package com.victorious.browser.webext

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * On-disk JSON store (filesDir/extensions/extensions.json), singleton like
 * [com.victorious.browser.userscript.UserScriptStore]. Writes are atomic (tmp + rename).
 * Paused-hosts list (per-site toggle from the browser) lives in SharedPreferences.
 */
class ExtensionStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(File(appContext.filesDir, "extensions").apply { mkdirs() }, "extensions.json")
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val registry = ExtensionRegistry(
        ExtensionCodec.decode(runCatching { if (file.exists()) file.readText() else null }.getOrNull()),
    ).also { it.pausedHosts = prefs.getStringSet(KEY_PAUSED, emptySet()).orEmpty().toSet() }

    private val _extensions = MutableStateFlow(registry.extensions)
    val extensions: StateFlow<List<Extension>> = _extensions.asStateFlow()
    private val _paused = MutableStateFlow(registry.pausedHosts)
    val pausedHosts: StateFlow<Set<String>> = _paused.asStateFlow()

    @Synchronized fun install(source: String, bundled: Boolean = false): ExtensionRegistry.InstallResult =
        registry.install(source, bundled).also { commit() }

    @Synchronized fun setEnabled(id: String, enabled: Boolean) { registry.setEnabled(id, enabled); commit() }
    @Synchronized fun delete(id: String) { registry.delete(id); commit() }
    @Synchronized fun forPage(url: String?, runAt: RunAt) = registry.forPage(url, runAt)

    @Synchronized fun getValue(id: String, key: String) = registry.getValue(id, key)
    @Synchronized fun setValue(id: String, key: String, value: String) { if (registry.setValue(id, key, value)) commit() }
    @Synchronized fun deleteValue(id: String, key: String) { if (registry.deleteValue(id, key)) commit() }
    @Synchronized fun listValues(id: String) = registry.listValues(id)

    @Synchronized fun setHostPaused(host: String, paused: Boolean) {
        val h = host.lowercase()
        registry.pausedHosts = if (paused) registry.pausedHosts + h else registry.pausedHosts - h
        prefs.edit().putStringSet(KEY_PAUSED, registry.pausedHosts).apply()
        _paused.value = registry.pausedHosts
    }

    /** Install bundled starters that aren't present yet (by name). Returns how many were added. */
    @Synchronized fun installStarters(): Int {
        var n = 0
        BundledUserscripts.load(appContext).forEach { src ->
            val meta = (UserscriptParser.parse(src) as? ParseResult.Ok)?.meta ?: return@forEach
            if (registry.extensions.none { it.name == meta.name && it.namespace == meta.namespace }) {
                if (registry.install(src, bundled = true) is ExtensionRegistry.InstallResult.Installed) n++
            }
        }
        commit()
        return n
    }

    private fun commit() {
        _extensions.value = registry.extensions
        runCatching {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(ExtensionCodec.encode(registry.extensions, registry.snapshotValues()))
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }

    companion object {
        private const val PREFS = "webext"
        private const val KEY_PAUSED = "paused_hosts"
        @Volatile private var instance: ExtensionStore? = null
        fun get(context: Context): ExtensionStore =
            instance ?: synchronized(this) { instance ?: ExtensionStore(context).also { instance = it } }
    }
}

object BundledUserscripts {
    const val ASSET_DIR = "userscripts"
    fun load(context: Context): List<String> = runCatching {
        context.assets.list(ASSET_DIR).orEmpty().filter { it.endsWith(".user.js") }.sorted()
            .map { n -> context.assets.open("$ASSET_DIR/$n").bufferedReader().use { it.readText() } }
    }.getOrDefault(emptyList())
}
