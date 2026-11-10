package com.victorious.browser.linkembed

/**
 * "Copy as embed" snippet builders. Pure Kotlin. Every snippet is built from a URL that
 * already passed [UrlSafety] plus escaped text, so a hostile page title or URL cannot
 * break out of the intended Markdown link / HTML attribute / element.
 */
object EmbedSnippets {

    data class Snippets(
        val markdown: String,
        val htmlLink: String,
        /** `<iframe>` markup, or null when recon did not say the page is embeddable. */
        val iframe: String?,
        /** Why [iframe] is null (shown in the UI and used for [iframeOrFallback]). */
        val iframeOmittedReason: String?,
        /** Raw provider oEmbed HTML (UNTRUSTED, copied verbatim, never escaped). */
        val oembedHtml: String?,
    ) {
        /** The iframe when allowed, otherwise a plain link prefixed with a fixed explanatory comment. */
        val iframeOrFallback: String
            get() = iframe ?: ("<!-- iframe omitted: embed recon did not report this page as embeddable -->\n$htmlLink")
    }

    /**
     * Builds all snippets for [url]. Returns null when [url] is not a safe http(s) URL.
     * [verdict] gates the iframe: only [EmbedRecon.Verdict.EMBEDDABLE] produces one.
     */
    fun build(url: String, title: String?, verdict: EmbedRecon.Verdict?, oembedHtml: String? = null): Snippets? {
        val safe = UrlSafety.sanitize(url) ?: return null
        val text = title?.let { HtmlMetadataParser.clean(it, HtmlMetadataParser.MAX_TITLE) }?.takeIf { it.isNotEmpty() } ?: safe
        val omitted = when (verdict) {
            EmbedRecon.Verdict.EMBEDDABLE -> null
            EmbedRecon.Verdict.NOT_EMBEDDABLE -> "Recon says this page refuses third-party framing (X-Frame-Options / CSP frame-ancestors)."
            EmbedRecon.Verdict.CONDITIONAL -> "Recon says only specific origins may frame this page."
            EmbedRecon.Verdict.UNKNOWN -> "Recon could not determine whether this page can be framed."
            null -> "Run embed recon first; an iframe is only offered when recon reports the page as embeddable."
        }
        return Snippets(
            markdown = markdown(safe, text),
            htmlLink = htmlLink(safe, text),
            iframe = if (omitted == null) iframe(safe, text) else null,
            iframeOmittedReason = omitted,
            oembedHtml = oembedHtml?.takeIf { it.isNotBlank() },
        )
    }

    private const val MD_SPECIAL = "\\`*_{}[]()<>#+-!|~"

    /** Escapes text for use inside Markdown link text `[...]`. Newlines collapse to spaces. */
    fun escapeMarkdownText(s: String): String = buildString(s.length + 8) {
        for (c in s.replace(Regex("[\\r\\n\\t]+"), " ")) {
            if (c in MD_SPECIAL) append('\\')
            append(c)
        }
    }

    /** Percent-encodes characters that could end or confuse a Markdown link destination. */
    fun escapeMarkdownUrl(url: String): String = buildString(url.length + 8) {
        for (c in url) when (c) {
            ' ' -> append("%20")
            '(' -> append("%28")
            ')' -> append("%29")
            '<' -> append("%3C")
            '>' -> append("%3E")
            '[' -> append("%5B")
            ']' -> append("%5D")
            '"' -> append("%22")
            '\'' -> append("%27")
            '`' -> append("%60")
            '\\' -> append("%5C")
            '\n', '\r', '\t' -> {}
            else -> append(c)
        }
    }

    /** Escapes for both HTML text and double/single-quoted attribute contexts. */
    fun escapeHtml(s: String): String = buildString(s.length + 16) {
        for (c in s) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            '`' -> append("&#96;")
            else -> append(c)
        }
    }

    fun markdown(url: String, title: String): String = "[${escapeMarkdownText(title)}](${escapeMarkdownUrl(url)})"

    fun htmlLink(url: String, title: String): String =
        "<a href=\"${escapeHtml(url)}\" rel=\"noopener noreferrer\" target=\"_blank\">${escapeHtml(title)}</a>"

    fun iframe(url: String, title: String, width: Int = 560, height: Int = 315): String =
        "<iframe src=\"${escapeHtml(url)}\" title=\"${escapeHtml(title)}\" width=\"$width\" height=\"$height\" " +
            "loading=\"lazy\" referrerpolicy=\"strict-origin-when-cross-origin\" " +
            "sandbox=\"allow-scripts allow-same-origin allow-popups allow-presentation\" " +
            "allow=\"fullscreen; picture-in-picture; encrypted-media\" allowfullscreen></iframe>"
}
