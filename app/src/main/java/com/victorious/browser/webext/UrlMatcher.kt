package com.victorious.browser.webext

// Chrome match-pattern (scheme://host/path) plus <all_urls>.
// Scheme * means http or https. Host *.x matches x and its subdomains.
object MatchPattern {
    private val hostAtom: String = "[^" + "/" + "*" + "]+"
    private val RE = Regex("^(\\*|https?|file|ftp|wss?)://(\\*|\\*\\." + hostAtom + "|" + hostAtom + ")?(/.*)\$")
    private val SCHEMES = setOf("http", "https", "file", "ftp", "ws", "wss")

    fun isValid(p: String): Boolean = p == "<all_urls>" || RE.matches(p.trim())

    fun matches(pattern: String, url: String): Boolean {
        val p = pattern.trim()
        val u = UrlParts.parse(url) ?: return false
        if (p == "<all_urls>") return u.scheme in SCHEMES
        val m = RE.matchEntire(p) ?: return false
        val (scheme, host, path) = m.destructured
        val schemeOk = if (scheme == "*") u.scheme == "http" || u.scheme == "https" else u.scheme == scheme
        if (!schemeOk) return false
        val hostOk = when {
            host.isEmpty() -> u.scheme == "file"
            host == "*" -> true
            host.startsWith("*.") -> {
                val bare = host.substring(2).lowercase()
                u.host == bare || u.host.endsWith(".$bare")
            }
            else -> u.host == host.lowercase()
        }
        if (!hostOk) return false
        return Glob.matches(path, u.pathAndQuery)
    }
}

// Minimal URL splitter (avoid android.net.Uri so it is JVM-testable).
data class UrlParts(val scheme: String, val host: String, val pathAndQuery: String) {
    companion object {
        fun parse(url: String): UrlParts? {
            val idx = url.indexOf("://")
            if (idx <= 0) return null
            val scheme = url.substring(0, idx).lowercase()
            val rest = url.substring(idx + 3).substringBefore('#')
            val slash = rest.indexOf('/').let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, slash).substringAfterLast('@')
            val host = if (authority.startsWith("[")) authority.substringBefore("]") + "]"
            else authority.substringBefore(':')
            val path = rest.substring(slash).ifEmpty { "/" }
            return UrlParts(scheme, host.lowercase(), if (path.startsWith("?")) "/$path" else path)
        }
    }
}

// Star = any run of chars, everything else literal. Iterative, no regex backtracking.
object Glob {
    fun matches(glob: String, text: String): Boolean {
        var g = 0; var t = 0; var star = -1; var mark = 0
        while (t < text.length) {
            if (g < glob.length && glob[g] != '*' && glob[g] == text[t]) { g++; t++ }
            else if (g < glob.length && glob[g] == '*') { star = g++; mark = t }
            else if (star >= 0) { g = star + 1; t = ++mark }
            else return false
        }
        while (g < glob.length && glob[g] == '*') g++
        return g == glob.length
    }
}

// Resolves @match / @include / @exclude for a URL. Excludes win.
object UrlMatcher {
    fun matchesRule(rule: String, url: String): Boolean {
        val r = rule.trim()
        if (r == "*") return true
        if (r.length >= 2 && r.startsWith("/")) {
            val last = r.lastIndexOf('/')
            if (last > 0) {
                val flags = r.substring(last + 1)
                if (flags.all { it in "igm" }) {
                    val opts = if ('i' in flags) setOf(RegexOption.IGNORE_CASE) else emptySet()
                    return runCatching { Regex(r.substring(1, last), opts).containsMatchIn(url) }
                        .getOrDefault(false)
                }
            }
        }
        if (MatchPattern.isValid(r)) return MatchPattern.matches(r, url)
        return Glob.matches(r, url)
    }

    fun shouldRun(meta: UserscriptMeta, url: String?): Boolean =
        shouldRun(meta.matches, meta.includes, meta.excludes, url)

    fun shouldRun(
        matches: List<String>,
        includes: List<String>,
        excludes: List<String>,
        url: String?,
    ): Boolean {
        if (url.isNullOrBlank()) return false
        if (excludes.any { matchesRule(it, url) }) return false
        return matches.any { MatchPattern.matches(it, url) } || includes.any { matchesRule(it, url) }
    }
}
