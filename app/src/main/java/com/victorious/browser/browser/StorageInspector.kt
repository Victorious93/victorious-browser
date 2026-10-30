package com.victorious.browser.browser

import com.victorious.browser.inspect.InspectHelpers
import org.json.JSONArray
import org.json.JSONObject

/**
 * localStorage / sessionStorage dump + mutate scripts for the current origin.
 *
 * The dump runs inside the page via `evaluateJavascript`. WebView JSON-encodes the
 * callback value a second time, so [parseDump] unwraps that the same way
 * [com.victorious.browser.inject.InjectionTools] does.
 */
object StorageInspector {

    data class Entry(
        val key: String,
        val value: String,
        val jwt: InspectHelpers.DecodedJwt?,
    )

    data class Dump(
        val origin: String,
        val local: List<Entry>,
        val session: List<Entry>,
        val error: String? = null,
    )

    fun dumpScript(): String = """
        (function() {
            function entries(store) {
                var a = [];
                try {
                    for (var i = 0; i < store.length; i++) {
                        var k = store.key(i);
                        a.push({k: String(k), v: String(store.getItem(k))});
                    }
                } catch (e) {
                    return {error: String(e)};
                }
                return a;
            }
            var local = entries(window.localStorage);
            var session = entries(window.sessionStorage);
            return JSON.stringify({
                origin: location.origin || "",
                localStorage: local,
                sessionStorage: session
            });
        })();
    """.trimIndent()

    fun setScript(store: Store, key: String, value: String): String {
        val storeName = store.jsName
        val k = JSONObject.quote(key)
        val v = JSONObject.quote(value)
        return """
            (function() {
                try {
                    $storeName.setItem($k, $v);
                    return JSON.stringify({ok: true});
                } catch (e) {
                    return JSON.stringify({ok: false, error: String(e)});
                }
            })();
        """.trimIndent()
    }

    fun removeScript(store: Store, key: String): String {
        val storeName = store.jsName
        val k = JSONObject.quote(key)
        return """
            (function() {
                try {
                    $storeName.removeItem($k);
                    return JSON.stringify({ok: true});
                } catch (e) {
                    return JSON.stringify({ok: false, error: String(e)});
                }
            })();
        """.trimIndent()
    }

    fun clearScript(store: Store): String {
        val storeName = store.jsName
        return """
            (function() {
                try {
                    $storeName.clear();
                    return JSON.stringify({ok: true});
                } catch (e) {
                    return JSON.stringify({ok: false, error: String(e)});
                }
            })();
        """.trimIndent()
    }

    enum class Store(val jsName: String) {
        LOCAL("localStorage"),
        SESSION("sessionStorage"),
    }

    /**
     * Unwrap an `evaluateJavascript` callback and parse a [Dump].
     * [callbackValue] is the raw callback string from WebView (double-encoded)
     * **or** the already-unwrapped JSON object/string used in unit tests.
     */
    fun parseDump(callbackValue: String?): Dump {
        val raw = unwrapEvaluateJavascript(callbackValue)
            ?: return Dump("", emptyList(), emptyList(), error = "No response from page")
        return try {
            val obj = JSONObject(raw)
            val origin = obj.optString("origin")
            val local = parseEntries(obj.opt("localStorage"), "localStorage")
            val session = parseEntries(obj.opt("sessionStorage"), "sessionStorage")
            val err = when {
                local.error != null && session.error != null ->
                    "localStorage: ${local.error}; sessionStorage: ${session.error}"
                local.error != null -> "localStorage: ${local.error}"
                session.error != null -> "sessionStorage: ${session.error}"
                else -> null
            }
            Dump(origin = origin, local = local.entries, session = session.entries, error = err)
        } catch (e: Exception) {
            Dump("", emptyList(), emptyList(), error = e.message ?: "parse error")
        }
    }

    fun parseMutation(callbackValue: String?): Pair<Boolean, String?> {
        val raw = unwrapEvaluateJavascript(callbackValue) ?: return false to "No response from page"
        return try {
            val obj = JSONObject(raw)
            val ok = obj.optBoolean("ok", false)
            val err = if (obj.has("error") && !obj.isNull("error")) obj.optString("error") else null
            ok to err
        } catch (e: Exception) {
            false to (e.message ?: "parse error")
        }
    }

    fun exportJson(dump: Dump, exportedAt: Long): String {
        val root = JSONObject()
            .put("tool", "victorious-browser.web-storage")
            .put("origin", dump.origin)
            .put("exportedAt", exportedAt)
        root.put("localStorage", entriesToJson(dump.local))
        root.put("sessionStorage", entriesToJson(dump.session))
        if (dump.error != null) root.put("error", dump.error)
        return root.toString(2)
    }

    /** WebView double-encodes `evaluateJavascript` results that are already JSON strings. */
    fun unwrapEvaluateJavascript(callbackValue: String?): String? {
        if (callbackValue.isNullOrBlank() || callbackValue == "null") return null
        val trimmed = callbackValue.trim()
        // Already a JSON object/array (unit tests, or a non-string JS result).
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return trimmed
        return try {
            JSONObject("{\"v\":$trimmed}").getString("v")
        } catch (_: Exception) {
            trimmed.removeSurrounding("\"")
        }
    }

    private data class Parsed(val entries: List<Entry>, val error: String?)

    private fun parseEntries(node: Any?, source: String): Parsed {
        if (node is JSONObject && node.has("error")) {
            return Parsed(emptyList(), node.optString("error"))
        }
        if (node !is JSONArray) return Parsed(emptyList(), null)
        val out = ArrayList<Entry>(node.length())
        for (i in 0 until node.length()) {
            val o = node.optJSONObject(i) ?: continue
            val key = o.optString("k")
            val value = o.optString("v")
            val jwt = InspectHelpers.decodeJwt(value, source = "$source:$key")
                ?: InspectHelpers.findJwtCandidates(value).firstOrNull()
                    ?.let { InspectHelpers.decodeJwt(it, source = "$source:$key") }
            out += Entry(key = key, value = value, jwt = jwt)
        }
        return Parsed(out, null)
    }

    private fun entriesToJson(entries: List<Entry>): JSONArray {
        val arr = JSONArray()
        entries.forEach { e ->
            val o = JSONObject().put("key", e.key).put("value", e.value)
            val jwt = e.jwt
            if (jwt != null) {
                val claims = JSONObject()
                jwt.claims.forEach { (k, v) -> claims.put(k, v) }
                o.put(
                    "jwt",
                    JSONObject()
                        .put("decodedOk", jwt.decodedOk)
                        .put("signatureVerified", false)
                        .put("note", "decoded-not-verified")
                        .put("claims", claims),
                )
            }
            arr.put(o)
        }
        return arr
    }
}
