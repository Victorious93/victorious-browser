package com.victorious.browser.osint.research

import org.json.JSONArray
import org.json.JSONObject

/**
 * Sherlock-style username presence check.
 *
 * The site list uses the same JSON shape as the Sherlock project's `data.json`
 * (MIT licensed): `{ "Site": { "url": "https://x/{}", "urlMain": "...", "errorType":
 * "status_code" | "message" | "response_url", "errorMsg": "..." | [...], "regexCheck": "..." } }`.
 * A small hand-picked list ships in assets; the full list can be fetched at runtime.
 *
 * Results are deliberately three-valued. Sites change, rate-limit and show captchas,
 * so anything that isn't a clear signal is UNKNOWN — never guessed as found.
 */
object UsernameSearch {
    const val SHERLOCK_DATA_URL =
        "https://raw.githubusercontent.com/sherlock-project/sherlock/master/sherlock_project/resources/data.json"

    enum class ErrorType { STATUS_CODE, MESSAGE, RESPONSE_URL, UNSUPPORTED }

    data class Site(
        val name: String,
        val urlTemplate: String,
        val urlMain: String,
        val errorType: ErrorType,
        val errorMessages: List<String>,
        val regexCheck: String?,
        val probeTemplate: String?,
    ) {
        fun profileUrl(username: String) = urlTemplate.replace("{}", DorkComposer.encode(username))
        fun probeUrl(username: String) = (probeTemplate ?: urlTemplate).replace("{}", DorkComposer.encode(username))
        fun accepts(username: String): Boolean {
            val rx: String = regexCheck ?: return true
            return runCatching { Regex(rx).containsMatchIn(username) }.getOrDefault(true)
        }
    }

    enum class Verdict { FOUND, NOT_FOUND, UNKNOWN, SKIPPED }

    data class Check(val site: Site, val verdict: Verdict, val url: String, val detail: String)

    private val VALID_USERNAME = Regex("^[A-Za-z0-9._-]{1,64}$")
    fun isSearchable(username: String) = VALID_USERNAME.matches(username.trim())

    /** Parse Sherlock-format JSON. Entries that aren't http(s) or lack `{}` are dropped. */
    fun parseSites(json: String): List<Site> {
        val root = JSONObject(json)
        val out = mutableListOf<Site>()
        for (key in root.keys()) {
            if (key.startsWith("$")) continue // "$schema"
            val o = root.optJSONObject(key) ?: continue
            val url = o.optString("url")
            if (!url.contains("{}") || !(url.startsWith("https://") || url.startsWith("http://"))) continue
            val type = when (o.optString("errorType")) {
                "status_code" -> ErrorType.STATUS_CODE
                "message" -> ErrorType.MESSAGE
                "response_url" -> ErrorType.RESPONSE_URL
                else -> ErrorType.UNSUPPORTED
            }
            val msgs = when (val m = o.opt("errorMsg")) {
                is String -> listOf(m)
                is JSONArray -> (0 until m.length()).map { m.optString(it) }
                else -> emptyList()
            }.filter { it.isNotEmpty() }
            out += Site(
                name = key,
                urlTemplate = url,
                urlMain = o.optString("urlMain").ifBlank { url.substringBefore("{}") },
                errorType = type,
                errorMessages = msgs,
                regexCheck = o.optString("regexCheck").ifBlank { null },
                probeTemplate = o.optString("urlProbe").ifBlank { null }?.takeIf { it.contains("{}") },
            )
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /**
     * Interpret one bounded GET of [Site.probeUrl].
     * @param redirected true when the final URL differs from the requested one.
     */
    fun evaluate(site: Site, status: Int?, body: String, redirected: Boolean, error: String?): Pair<Verdict, String> {
        if (error != null && status == null) return Verdict.UNKNOWN to "No response: $error"
        val s = status ?: return Verdict.UNKNOWN to "No status"
        if (s == 429) return Verdict.UNKNOWN to "Rate limited (429)"
        if (s == 403 || s == 401) return Verdict.UNKNOWN to "Blocked/needs login ($s)"
        if (s >= 500) return Verdict.UNKNOWN to "Server error ($s)"
        if (looksLikeChallenge(body)) return Verdict.UNKNOWN to "Bot check / captcha page"
        return when (site.errorType) {
            ErrorType.STATUS_CODE -> when {
                s in 200..299 -> Verdict.FOUND to "HTTP $s"
                s == 404 || s == 410 -> Verdict.NOT_FOUND to "HTTP $s"
                else -> Verdict.UNKNOWN to "HTTP $s"
            }
            ErrorType.MESSAGE -> when {
                site.errorMessages.isEmpty() -> Verdict.UNKNOWN to "Site entry has no error message"
                site.errorMessages.any { body.contains(it) } -> Verdict.NOT_FOUND to "Error text present"
                s in 200..299 -> Verdict.FOUND to "HTTP $s, no error text"
                s == 404 -> Verdict.NOT_FOUND to "HTTP 404"
                else -> Verdict.UNKNOWN to "HTTP $s"
            }
            ErrorType.RESPONSE_URL -> when {
                redirected -> Verdict.NOT_FOUND to "Redirected away"
                s in 200..299 -> Verdict.FOUND to "HTTP $s, no redirect"
                s == 404 -> Verdict.NOT_FOUND to "HTTP 404"
                else -> Verdict.UNKNOWN to "HTTP $s"
            }
            ErrorType.UNSUPPORTED -> Verdict.UNKNOWN to "Detection method not supported"
        }
    }

    private val CHALLENGE = listOf("cf-chl-", "challenge-platform", "captcha", "Just a moment...", "Attention Required!")
    fun looksLikeChallenge(body: String): Boolean {
        val head = body.take(20_000)
        return CHALLENGE.any { head.contains(it, ignoreCase = true) }
    }

    fun summarize(results: List<Check>): Map<Verdict, Int> = Verdict.entries.associateWith { v -> results.count { it.verdict == v } }
}
