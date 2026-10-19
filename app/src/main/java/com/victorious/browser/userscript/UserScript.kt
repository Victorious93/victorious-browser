package com.victorious.browser.userscript

/**
 * A user script, in the spirit of Hermit's "run your own custom extension scripts".
 *
 * [hostPattern] is a bare host (e.g. "example.com") or a leading-wildcard host
 * ("*.example.com") — matched the same way as [com.victorious.browser.inspect.ContentBlocker]:
 * exact match or subdomain-of. It is converted to WebView origin-match rules at
 * injection time ([UserScriptInjector.originRulesFor]); it is never treated as a
 * URL path pattern.
 */
data class UserScript(
    val id: String,
    val name: String,
    val hostPattern: String,
    val code: String,
    val enabled: Boolean = true,
)

/** Pure host-pattern matching, shared by the injector and its tests. */
object UserScriptMatcher {
    fun bareHost(pattern: String): String = pattern.removePrefix("*.").lowercase()

    fun matches(host: String?, pattern: String): Boolean {
        if (host.isNullOrBlank() || pattern.isBlank()) return false
        val h = host.lowercase().removeSuffix(".")
        val bare = bareHost(pattern)
        return h == bare || h.endsWith(".$bare")
    }
}
