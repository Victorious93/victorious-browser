package com.victorious.browser.webext

/**
 * Parsed `==UserScript==` metadata block (Greasemonkey / Tampermonkey style).
 *
 * This is a WebView-native userscript engine — NOT a Chrome/Firefox WebExtension
 * host. A system WebView has no WebExtension API, so `.crx` / `.xpi` packages
 * cannot run here. Only the script logic is injected into pages.
 */
enum class RunAt(val token: String) {
    DOCUMENT_START("document-start"),
    DOCUMENT_END("document-end"),
    DOCUMENT_IDLE("document-idle");

    companion object {
        fun parse(raw: String?): RunAt =
            entries.firstOrNull { it.token.equals(raw?.trim(), ignoreCase = true) } ?: DOCUMENT_IDLE
    }
}

data class UserscriptMeta(
    val name: String,
    val namespace: String = "",
    val version: String = "",
    val description: String = "",
    val matches: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val runAt: RunAt = RunAt.DOCUMENT_IDLE,
    val grants: List<String> = emptyList(),
    val requires: List<String> = emptyList(),
    val icon: String = "",
)

sealed class ParseResult {
    data class Ok(val meta: UserscriptMeta, val warnings: List<String>) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

/** Hand-rolled metadata parser. Pure Kotlin, no deps. */
object UserscriptParser {
    private const val OPEN = "==UserScript=="
    private const val CLOSE = "==/UserScript=="
    private val LINE = Regex("""^\s*//\s*@([A-Za-z][\w:-]*)(?:\s+(.*?))?\s*$""")

    fun parse(source: String): ParseResult {
        val lines = source.lines()
        val start = lines.indexOfFirst { it.trim().startsWith("//") && it.contains(OPEN) }
        if (start < 0) return ParseResult.Error("No ==UserScript== metadata block found")
        val end = (start + 1 until lines.size).firstOrNull { lines[it].contains(CLOSE) }
            ?: return ParseResult.Error("Metadata block is not closed with ==/UserScript==")

        val tags = mutableListOf<Pair<String, String>>()
        for (i in start + 1 until end) {
            val m = LINE.find(lines[i]) ?: continue
            tags += m.groupValues[1].lowercase() to m.groupValues[2].trim()
        }
        fun first(k: String) = tags.firstOrNull { it.first == k }?.second.orEmpty()
        fun all(k: String) = tags.filter { it.first == k }.map { it.second }.filter { it.isNotBlank() }

        val name = first("name")
        if (name.isBlank()) return ParseResult.Error("@name is required")
        val warnings = mutableListOf<String>()
        val matches = all("match")
        matches.filterNot { MatchPattern.isValid(it) }.forEach { warnings += "Invalid @match ignored: $it" }
        val includes = all("include")
        val requires = all("require")
        if (requires.isNotEmpty()) warnings += "@require is recorded but NOT fetched (no remote code loading)"
        if (matches.isEmpty() && includes.isEmpty()) warnings += "No @match/@include — script will never run"
        val grants = all("grant").filterNot { it == "none" }
        grants.filterNot { it in GmShim.SUPPORTED_GRANTS }.forEach { warnings += "@grant $it is stubbed/unsupported" }
        val icon = first("icon").ifBlank { first("iconurl") }

        return ParseResult.Ok(
            UserscriptMeta(
                name = name,
                namespace = first("namespace"),
                version = first("version"),
                description = first("description"),
                matches = matches.filter { MatchPattern.isValid(it) },
                includes = includes,
                excludes = all("exclude") + all("exclude-match"),
                runAt = RunAt.parse(first("run-at")),
                grants = grants,
                requires = requires,
                icon = icon,
            ),
            warnings,
        )
    }
}
