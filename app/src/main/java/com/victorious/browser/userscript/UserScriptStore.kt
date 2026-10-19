package com.victorious.browser.userscript

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local store for user scripts. SharedPreferences + JSON, same pattern as
 * [com.victorious.browser.panels.PanelStore] — no scripts are seeded by default.
 */
class UserScriptStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _scripts = MutableStateFlow(load())
    val scripts: StateFlow<List<UserScript>> = _scripts.asStateFlow()

    fun add(script: UserScript) {
        _scripts.value = _scripts.value + script
        persist()
    }

    fun update(script: UserScript) {
        _scripts.value = _scripts.value.map { if (it.id == script.id) script else it }
        persist()
    }

    fun delete(id: String) {
        _scripts.value = _scripts.value.filterNot { it.id == id }
        persist()
    }

    /** Enabled scripts matching [host], in stored order. */
    fun scriptsFor(host: String?): List<UserScript> =
        _scripts.value.filter { it.enabled && UserScriptMatcher.matches(host, it.hostPattern) }

    private fun persist() {
        val arr = JSONArray()
        _scripts.value.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<UserScript> {
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
        private const val PREFS = "user_scripts"
        private const val KEY = "scripts_json"

        @Volatile private var instance: UserScriptStore? = null

        fun get(context: Context): UserScriptStore =
            instance ?: synchronized(this) {
                instance ?: UserScriptStore(context).also { instance = it }
            }

        fun newId(): String = UUID.randomUUID().toString()

        private fun toJson(s: UserScript): JSONObject = JSONObject()
            .put("id", s.id)
            .put("name", s.name)
            .put("hostPattern", s.hostPattern)
            .put("code", s.code)
            .put("enabled", s.enabled)

        private fun fromJson(o: JSONObject): UserScript = UserScript(
            id = o.getString("id"),
            name = o.getString("name"),
            hostPattern = o.getString("hostPattern"),
            code = o.getString("code"),
            enabled = o.optBoolean("enabled", true),
        )
    }
}
