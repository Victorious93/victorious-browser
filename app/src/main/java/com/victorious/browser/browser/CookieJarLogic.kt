package com.victorious.browser.browser

import com.victorious.browser.inspect.InspectHelpers
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cookie-jar helpers for the live [android.webkit.CookieManager] inspector.
 *
 * CookieManager speaks the Cookie header (`name=value; name2=value2`) and accepts
 * Set-Cookie-style strings for writes. HttpOnly vs JS-accessible is NOT distinguishable
 * from getCookie() — Android returns every cookie that would be sent on a request.
 * JWT values are decoded-not-verified via [InspectHelpers].
 */
object CookieJarLogic {

    data class JarCookie(
        val name: String,
        val value: String,
        val jwt: InspectHelpers.DecodedJwt?,
    )

    fun parseJar(header: String?): List<JarCookie> {
        if (header.isNullOrBlank()) return emptyList()
        return InspectHelpers.parseCookieHeader(header).map { c ->
            JarCookie(
                name = c.name,
                value = c.value,
                jwt = InspectHelpers.decodeJwt(c.value, source = "cookie:${c.name}")
                    ?: InspectHelpers.findJwtCandidates(c.value).firstOrNull()
                        ?.let { InspectHelpers.decodeJwt(it, source = "cookie:${c.name}") },
            )
        }
    }

    /** Set-Cookie line CookieManager understands for an upsert. */
    fun setCookieLine(name: String, value: String, path: String = "/"): String {
        val n = name.trim()
        require(n.isNotEmpty() && ';' !in n && '=' !in n) { "Invalid cookie name" }
        return "$n=$value; Path=$path"
    }

    /** Expire a cookie so CookieManager drops it for this URL. */
    fun expireCookieLine(name: String, path: String = "/"): String {
        val n = name.trim()
        require(n.isNotEmpty() && ';' !in n && '=' !in n) { "Invalid cookie name" }
        return "$n=; Path=$path; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT"
    }

    fun exportJson(url: String, cookies: List<JarCookie>, exportedAt: Long): String {
        val root = JSONObject()
        root.put("tool", "victorious-browser.cookie-jar")
        root.put("url", url)
        root.put("origin", SiteData.originOf(url) ?: JSONObject.NULL)
        root.put("exportedAt", exportedAt)
        val arr = JSONArray()
        cookies.forEach { c ->
            val o = JSONObject()
                .put("name", c.name)
                .put("value", c.value)
            val jwt = c.jwt
            if (jwt != null) {
                val claims = JSONObject()
                jwt.claims.forEach { (k, v) -> claims.put(k, v) }
                o.put(
                    "jwt",
                    JSONObject()
                        .put("decodedOk", jwt.decodedOk)
                        .put("signatureVerified", false)
                        .put("note", "decoded-not-verified")
                        .put("header", jwt.header ?: JSONObject.NULL)
                        .put("payload", jwt.payload ?: JSONObject.NULL)
                        .put("claims", claims),
                )
            } else {
                o.put("jwt", JSONObject.NULL)
            }
            arr.put(o)
        }
        root.put("cookies", arr)
        return root.toString(2)
    }
}
