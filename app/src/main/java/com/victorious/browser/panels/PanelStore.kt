package com.victorious.browser.panels

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local store for fleet panels.
 *
 * Implementation choice: SharedPreferences + JSON array. Chosen over Room because
 * the panel list is small, editable, and does not need relational queries — zero
 * KSP/schema overhead for this launcher layer. Swap to Room later if needed.
 *
 * Persist across launches. Seeded once with editable DEFAULT panels (placeholder
 * localhost URLs — the owner configures real addresses; no credentials / private
 * IPs are invented here).
 *
 * TODO(control-bridge): `browser.panel.list` / `browser.panel.open` will read/drive
 * this store. Do NOT build the Messenger bridge now.
 */
class PanelStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _panels = MutableStateFlow(load())
    val panels: StateFlow<List<Panel>> = _panels.asStateFlow()

    fun add(panel: Panel) {
        _panels.value = _panels.value + panel
        persist()
    }

    fun update(panel: Panel) {
        _panels.value = _panels.value.map { if (it.id == panel.id) panel else it }
        persist()
    }

    fun delete(id: String) {
        _panels.value = _panels.value.filterNot { it.id == id }
        persist()
    }

    fun get(id: String): Panel? = _panels.value.firstOrNull { it.id == id }

    private fun persist() {
        val arr = JSONArray()
        _panels.value.forEach { p -> arr.put(panelToJson(p)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<Panel> {
        val raw = prefs.getString(KEY, null)
        if (raw.isNullOrBlank()) {
            val defaults = defaultPanels()
            // Seed once so the owner can edit/delete freely afterward.
            val arr = JSONArray()
            defaults.forEach { arr.put(panelToJson(it)) }
            prefs.edit().putString(KEY, arr.toString()).apply()
            return defaults
        }
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(panelFromJson(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            defaultPanels()
        }
    }

    companion object {
        private const val PREFS = "fleet_panels"
        private const val KEY = "panels_json"

        @Volatile private var instance: PanelStore? = null

        fun get(context: Context): PanelStore =
            instance ?: synchronized(this) {
                instance ?: PanelStore(context).also { instance = it }
            }

        /**
         * Editable DEFAULT fleet panels. Placeholder localhost URLs/ports — the owner
         * configures real addresses. Not hardcoded secrets; no private IPs invented.
         */
        fun defaultPanels(): List<Panel> = listOf(
            Panel(
                id = UUID.randomUUID().toString(),
                name = "KYC-AuditLab",
                // Owner configures the real local port; placeholder only.
                url = "http://127.0.0.1:8080/",
                kind = PanelKind.KYC_AUDITLAB,
                reachOverMesh = false,
            ),
            Panel(
                id = UUID.randomUUID().toString(),
                name = "Headscale admin",
                // Placeholder — owner sets the real Headscale admin URL.
                url = "http://127.0.0.1:8081/admin",
                kind = PanelKind.HEADSCALE_ADMIN,
                reachOverMesh = true,
            ),
            Panel(
                id = UUID.randomUUID().toString(),
                name = "Metasploit web console",
                // Placeholder — owner sets the real MSF web UI URL.
                url = "http://127.0.0.1:3790/",
                kind = PanelKind.METASPLOIT_WEB,
                reachOverMesh = true,
            ),
            Panel(
                id = UUID.randomUUID().toString(),
                name = "Local HTTP tool",
                // Generic template for any localhost dashboard.
                url = "http://127.0.0.1:3000/",
                kind = PanelKind.LOCAL_HTTP,
                reachOverMesh = false,
            ),
        )

        private fun panelToJson(p: Panel): JSONObject = JSONObject()
            .put("id", p.id)
            .put("name", p.name)
            .put("url", p.url)
            .put("kind", p.kind.name)
            .put("reachOverMesh", p.reachOverMesh)

        private fun panelFromJson(o: JSONObject): Panel = Panel(
            id = o.getString("id"),
            name = o.getString("name"),
            url = o.getString("url"),
            kind = runCatching { PanelKind.valueOf(o.getString("kind")) }.getOrDefault(PanelKind.OTHER),
            reachOverMesh = o.optBoolean("reachOverMesh", false),
        )
    }
}
