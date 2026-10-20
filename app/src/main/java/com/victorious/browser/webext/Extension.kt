package com.victorious.browser.webext

import org.json.JSONArray
import org.json.JSONObject

/** An installed userscript-style extension. */
data class Extension(
    val id: String,
    val name: String,
    val version: String,
    val enabled: Boolean,
    val source: String,
    val matches: List<String>,
    val includes: List<String>,
    val excludes: List<String>,
    val grants: List<String>,
    val runAt: RunAt,
    val description: String = "",
    val namespace: String = "",
    val bundled: Boolean = false,
    val createdAt: Long,
) {
    fun appliesTo(url: String?): Boolean = enabled && UrlMatcher.shouldRun(matches, includes, excludes, url)

    companion object {
        fun fromMeta(id: String, meta: UserscriptMeta, source: String, createdAt: Long, bundled: Boolean = false) =
            Extension(
                id = id, name = meta.name, version = meta.version, enabled = true, source = source,
                matches = meta.matches, includes = meta.includes, excludes = meta.excludes,
                grants = meta.grants, runAt = meta.runAt, description = meta.description,
                namespace = meta.namespace, bundled = bundled, createdAt = createdAt,
            )
    }
}

/** Pure JSON codec for the on-disk store (JVM-testable via org.json test dep). */
object ExtensionCodec {
    const val VERSION = 1

    private fun arr(l: List<String>) = JSONArray().also { a -> l.forEach { a.put(it) } }
    private fun list(o: JSONObject, k: String): List<String> {
        val a = o.optJSONArray(k) ?: return emptyList()
        return (0 until a.length()).map { a.getString(it) }
    }

    fun toJson(e: Extension): JSONObject = JSONObject()
        .put("id", e.id).put("name", e.name).put("version", e.version).put("enabled", e.enabled)
        .put("source", e.source).put("matches", arr(e.matches)).put("includes", arr(e.includes))
        .put("excludes", arr(e.excludes)).put("grants", arr(e.grants)).put("runAt", e.runAt.token)
        .put("description", e.description).put("namespace", e.namespace).put("bundled", e.bundled)
        .put("createdAt", e.createdAt)

    fun fromJson(o: JSONObject): Extension = Extension(
        id = o.getString("id"), name = o.getString("name"), version = o.optString("version"),
        enabled = o.optBoolean("enabled", true), source = o.getString("source"),
        matches = list(o, "matches"), includes = list(o, "includes"), excludes = list(o, "excludes"),
        grants = list(o, "grants"), runAt = RunAt.parse(o.optString("runAt")),
        description = o.optString("description"), namespace = o.optString("namespace"),
        bundled = o.optBoolean("bundled", false), createdAt = o.optLong("createdAt", 0L),
    )

    fun encode(list: List<Extension>, values: Map<String, Map<String, String>>): String {
        val vals = JSONObject()
        values.forEach { (id, kv) ->
            val o = JSONObject()
            kv.forEach { (k, v) -> o.put(k, v) }
            vals.put(id, o)
        }
        return JSONObject()
            .put("v", VERSION)
            .put("extensions", JSONArray().also { a -> list.forEach { a.put(toJson(it)) } })
            .put("values", vals)
            .toString()
    }

    data class Decoded(val extensions: List<Extension>, val values: Map<String, Map<String, String>>)

    fun decode(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(emptyList(), emptyMap())
        return try {
            val root = JSONObject(raw)
            val a = root.optJSONArray("extensions") ?: JSONArray()
            val exts = (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
            val v = root.optJSONObject("values")
            val values = buildMap {
                v?.keys()?.forEach { id ->
                    val o = v.getJSONObject(id)
                    put(id, buildMap { o.keys().forEach { k -> put(k, o.getString(k)) } })
                }
            }
            Decoded(exts, values)
        } catch (_: Exception) {
            Decoded(emptyList(), emptyMap())
        }
    }
}
