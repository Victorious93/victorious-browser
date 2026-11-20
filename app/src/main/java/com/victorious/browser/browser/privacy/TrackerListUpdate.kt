package com.victorious.browser.browser.privacy

import org.json.JSONObject

/**
 * Format + validation for an optional tracker-list update. No backend is involved: the
 * user points the app at any HTTPS URL they trust (for example a raw file in their own
 * repository) and the downloaded list is merged with the bundled one.
 *
 * ```json
 * {"version": 3,
 *  "advertising": ["ads.example.net"],
 *  "analytics": [], "social": [], "session_replay": [], "cryptomining": []}
 * ```
 *
 * Validation is deliberately strict because an update can only ever ADD blocked hosts:
 * entries must be plain lowercase hostnames, public suffixes ("com", "co.uk", "github.io")
 * are rejected so a list cannot block a whole TLD, and size is capped.
 */
object TrackerListUpdate {
    const val MAX_BYTES = 512 * 1024
    const val MAX_HOSTS = 50_000

    private val HOST_RE = Regex("""^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$""")

    private val KEYS = mapOf(
        "advertising" to TrackerCategory.ADVERTISING,
        "analytics" to TrackerCategory.ANALYTICS,
        "social" to TrackerCategory.SOCIAL,
        "session_replay" to TrackerCategory.SESSION_REPLAY,
        "cryptomining" to TrackerCategory.CRYPTOMINING,
    )

    data class Parsed(val version: Int, val hosts: Map<TrackerCategory, Set<String>>, val rejected: Int) {
        val count: Int get() = hosts.values.sumOf { it.size }
    }

    /** True for an https URL with a host; the only scheme an update may be fetched over. */
    fun isAllowedUrl(url: String): Boolean = try {
        val u = java.net.URI(url.trim())
        u.scheme.equals("https", ignoreCase = true) && !u.host.isNullOrBlank() && u.userInfo == null
    } catch (_: Exception) {
        false
    }

    fun parse(json: String, psl: PublicSuffixList = PublicSuffixes.current): Result<Parsed> = runCatching {
        require(json.length <= MAX_BYTES) { "List is larger than ${MAX_BYTES / 1024} KB" }
        val root = JSONObject(json)
        val version = root.optInt("version", 0)
        val out = HashMap<TrackerCategory, MutableSet<String>>()
        var rejected = 0
        var total = 0
        for ((key, cat) in KEYS) {
            val arr = root.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                val h = arr.optString(i, "").trim().lowercase().removeSuffix(".")
                if (!HOST_RE.matches(h) || h.length > 253 || psl.isPublicSuffix(h)) { rejected++; continue }
                total++
                require(total <= MAX_HOSTS) { "List has more than $MAX_HOSTS hosts" }
                out.getOrPut(cat) { HashSet() }.add(h)
            }
        }
        require(out.isNotEmpty()) { "No valid hosts found" + if (rejected > 0) " ($rejected rejected)" else "" }
        Parsed(version, out, rejected)
    }
}
