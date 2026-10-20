package com.victorious.browser.osint

import java.net.URLEncoder

/**
 * Pure, Android-free Google-dork construction + search-engine URL building.
 *
 * Nothing here touches the network. The UI collects operator tokens, this object
 * joins them into a final query string and wraps that string in a search-engine
 * URL. The operator (the human) is the one who opens the URL in the WebView —
 * the app never auto-scrapes results.
 */
object DorkBuilder {

    /** Supported search engines and the query-string prefix each uses. */
    enum class Engine(val label: String, val base: String) {
        GOOGLE("Google", "https://www.google.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        BING("Bing", "https://www.bing.com/search?q="),
    }

    /**
     * Operators that take a single value, formatted as `name:value`.
     * [quoteValue] wraps the value in quotes when it contains whitespace.
     */
    enum class Operator(val label: String, val keyword: String) {
        SITE("site:", "site"),
        INURL("inurl:", "inurl"),
        INTITLE("intitle:", "intitle"),
        INTEXT("intext:", "intext"),
        FILETYPE("filetype:", "filetype"),
        EXT("ext:", "ext"),
        CACHE("cache:", "cache"),
        RELATED("related:", "related"),
        LINK("link:", "link");

        /** Format this operator with [value], e.g. `site:example.com`. */
        fun format(value: String): String {
            val v = value.trim()
            if (v.isEmpty()) return ""
            val needsQuotes = v.any { it.isWhitespace() }
            val rendered = if (needsQuotes) "\"$v\"" else v
            return "$keyword:$rendered"
        }
    }

    // --- single-token helpers (all pure string formatting) ----------------------

    /** `"exact phrase"` — an exact-match quoted term. */
    fun exact(phrase: String): String {
        val p = phrase.trim()
        return if (p.isEmpty()) "" else "\"$p\""
    }

    /** `-term` — exclude a term from results. Accepts a plain word or `op:value`. */
    fun exclude(term: String): String {
        val t = term.trim()
        return if (t.isEmpty()) "" else "-$t"
    }

    /** `a OR b` boolean OR between already-formatted tokens. */
    fun or(vararg terms: String): String =
        terms.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" OR ")

    /** `a AND b` boolean AND between already-formatted tokens. */
    fun and(vararg terms: String): String =
        terms.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" AND ")

    /** `a * b` — wildcard between two tokens (single wildcard if one side blank). */
    fun wildcard(left: String, right: String = ""): String {
        val l = left.trim()
        val r = right.trim()
        return listOf(l, "*", r).filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** `from..to` numeric range (either bound may be blank for open ranges). */
    fun numericRange(from: String, to: String): String {
        val f = from.trim()
        val t = to.trim()
        if (f.isEmpty() && t.isEmpty()) return ""
        return "$f..$t"
    }

    // --- assembly ---------------------------------------------------------------

    /**
     * Join already-formatted tokens into a single query string, dropping blanks
     * and collapsing internal whitespace.
     */
    fun buildQuery(tokens: List<String>): String =
        tokens.map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Wrap a finished query string in a search-engine URL. */
    fun searchUrl(engine: Engine, query: String): String =
        engine.base + encode(query)

    // --- quick templates --------------------------------------------------------

    /** A preset that expands to a token list for a given target domain. */
    data class Template(
        val name: String,
        val description: String,
        val build: (domain: String) -> List<String>,
    )

    /**
     * Built-in dork presets. Each takes the target domain and returns the tokens
     * the UI loads into the builder; the operator reviews before opening.
     */
    val templates: List<Template> = listOf(
        Template(
            "Exposed files",
            "Common downloadable/office/document types on the target.",
        ) { d ->
            listOf(
                Operator.SITE.format(d),
                or(
                    Operator.FILETYPE.format("pdf"),
                    Operator.FILETYPE.format("xls"),
                    Operator.FILETYPE.format("xlsx"),
                    Operator.FILETYPE.format("doc"),
                    Operator.FILETYPE.format("docx"),
                    Operator.FILETYPE.format("csv"),
                ),
            )
        },
        Template(
            "Login pages",
            "Pages whose URL/title looks like an auth/login portal.",
        ) { d ->
            listOf(
                Operator.SITE.format(d),
                or(
                    Operator.INURL.format("login"),
                    Operator.INURL.format("signin"),
                    Operator.INURL.format("admin"),
                    Operator.INTITLE.format("login"),
                ),
            )
        },
        Template(
            "Config / backup files",
            "Exposed configuration, env and backup artifacts.",
        ) { d ->
            listOf(
                Operator.SITE.format(d),
                or(
                    Operator.EXT.format("env"),
                    Operator.EXT.format("ini"),
                    Operator.EXT.format("conf"),
                    Operator.EXT.format("bak"),
                    Operator.EXT.format("old"),
                    Operator.EXT.format("sql"),
                ),
            )
        },
        Template(
            "Directory listings",
            "Auto-generated open directory index pages.",
        ) { d ->
            listOf(
                Operator.SITE.format(d),
                Operator.INTITLE.format("index of"),
                exact("parent directory"),
            )
        },
        Template(
            "Error messages",
            "Pages leaking stack traces / SQL / server errors.",
        ) { d ->
            listOf(
                Operator.SITE.format(d),
                or(
                    Operator.INTEXT.format("sql syntax near"),
                    Operator.INTEXT.format("fatal error"),
                    Operator.INTEXT.format("stack trace"),
                    Operator.INTEXT.format("warning: include"),
                ),
            )
        },
        Template(
            "Exposed credentials",
            "Pages/text that may expose passwords or API keys.",
        ) { d ->
            listOf(
                Operator.SITE.format(d),
                or(
                    Operator.INTEXT.format("password"),
                    Operator.INTEXT.format("api_key"),
                    Operator.INTEXT.format("secret"),
                ),
                or(
                    Operator.EXT.format("log"),
                    Operator.EXT.format("txt"),
                    Operator.FILETYPE.format("env"),
                ),
            )
        },
    )

    // --- internals --------------------------------------------------------------

    private fun encode(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
