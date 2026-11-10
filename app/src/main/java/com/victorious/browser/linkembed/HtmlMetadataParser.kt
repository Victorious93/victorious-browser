package com.victorious.browser.linkembed

/** Metadata extracted from a page for the preview card. All URLs are absolute and http(s)-only. */
data class LinkMetadata(
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val siteName: String? = null,
    val imageUrl: String? = null,
    val imageAlt: String? = null,
    val faviconUrl: String? = null,
    val canonicalUrl: String? = null,
    val type: String? = null,
    /** JSON oEmbed endpoint advertised via `<link rel="alternate" type="application/json+oembed">`. */
    val oembedUrl: String? = null,
    /** Which source supplied the title: "og", "twitter", "title", or "url". */
    val titleSource: String = "url",
    /**
     * Content of `<meta http-equiv="Content-Security-Policy" content="…">` tags found in
     * the document (first-wins order). Informational only — browsers do NOT enforce
     * `frame-ancestors` from a meta CSP (headers are authoritative).
     */
    val metaCsp: List<String> = emptyList(),
)

/**
 * Small, dependency-free HTML metadata scanner for Open Graph, Twitter Card, oEmbed
 * discovery, `<title>`, `<meta name=description>`, `<link rel=icon>` and `<base href>`.
 *
 * It is not a full HTML parser: it walks tags with a tolerant attribute reader, skips
 * comments and `<script>` / `<style>` bodies (so markup inside scripts is never
 * mistaken for a tag), decodes common character references, and caps every field.
 * Precedence: Open Graph → Twitter Card → generic HTML fallbacks. Pure Kotlin.
 */
object HtmlMetadataParser {
    const val MAX_TITLE = 300
    const val MAX_DESCRIPTION = 1000
    const val MAX_SITE_NAME = 120

    private data class Tag(val name: String, val attrs: Map<String, String>, val end: Int)

    fun parse(html: String, pageUrl: String): LinkMetadata {
        val meta = HashMap<String, String>() // first value wins per key
        var titleTag: String? = null
        var baseHref: String? = null
        var icon: String? = null
        var appleIcon: String? = null
        var canonical: String? = null
        var oembed: String? = null
        val metaCsp = mutableListOf<String>()

        var i = 0
        val n = html.length
        while (i < n) {
            val lt = html.indexOf('<', i)
            if (lt < 0 || lt + 1 >= n) break
            if (html.startsWith("<!--", lt)) {
                val close = html.indexOf("-->", lt + 4)
                i = if (close < 0) n else close + 3
                continue
            }
            val c = html[lt + 1]
            if (!c.isLetter()) { i = lt + 1; continue }
            val tag = readTag(html, lt)
            if (tag == null) { i = lt + 1; continue }
            i = tag.end
            when (tag.name) {
                "script", "style", "noscript", "template", "textarea" -> {
                    val close = html.indexOf("</${tag.name}", i, ignoreCase = true)
                    i = if (close < 0) n else close
                }
                "title" -> {
                    val close = html.indexOf("</title", i, ignoreCase = true)
                    if (titleTag == null && close >= 0) titleTag = html.substring(i, close)
                    i = if (close < 0) n else close
                }
                "base" -> if (baseHref == null) baseHref = tag.attrs["href"]
                "meta" -> {
                    val httpEquiv = tag.attrs["http-equiv"]?.trim()
                    val content = tag.attrs["content"]
                    if (httpEquiv != null && content != null &&
                        httpEquiv.equals("Content-Security-Policy", ignoreCase = true)
                    ) {
                        metaCsp += content.trim()
                    }
                    val key = (tag.attrs["property"] ?: tag.attrs["name"] ?: tag.attrs["itemprop"])?.trim()?.lowercase()
                    if (key != null && content != null && key !in meta) meta[key] = content
                }
                "link" -> {
                    val rels = tag.attrs["rel"]?.lowercase()?.split(Regex("\\s+")).orEmpty()
                    val href = tag.attrs["href"]
                    val type = tag.attrs["type"]?.lowercase()?.trim()
                    if (href != null) {
                        if ("icon" in rels && icon == null) icon = href
                        if (("apple-touch-icon" in rels || "apple-touch-icon-precomposed" in rels) && appleIcon == null) appleIcon = href
                        if ("canonical" in rels && canonical == null) canonical = href
                        if ("alternate" in rels && type == "application/json+oembed" && oembed == null) oembed = href
                    }
                }
            }
        }

        val base = baseHref?.let { UrlSafety.resolve(pageUrl, decode(it)) } ?: pageUrl
        fun abs(v: String?): String? = v?.let { UrlSafety.resolve(base, decode(it).trim()) }
        fun txt(v: String?, max: Int): String? = v?.let { clean(decode(it), max) }?.takeIf { it.isNotEmpty() }

        val ogTitle = txt(meta["og:title"], MAX_TITLE)
        val twTitle = txt(meta["twitter:title"], MAX_TITLE)
        val htmlTitle = txt(titleTag, MAX_TITLE)
        val (title, source) = when {
            ogTitle != null -> ogTitle to "og"
            twTitle != null -> twTitle to "twitter"
            htmlTitle != null -> htmlTitle to "title"
            else -> null to "url"
        }
        val image = abs(meta["og:image:secure_url"]) ?: abs(meta["og:image"]) ?: abs(meta["og:image:url"])
            ?: abs(meta["twitter:image"]) ?: abs(meta["twitter:image:src"])
        return LinkMetadata(
            url = pageUrl,
            title = title,
            description = txt(meta["og:description"], MAX_DESCRIPTION)
                ?: txt(meta["twitter:description"], MAX_DESCRIPTION)
                ?: txt(meta["description"], MAX_DESCRIPTION),
            siteName = txt(meta["og:site_name"], MAX_SITE_NAME)
                ?: txt(meta["application-name"], MAX_SITE_NAME)
                ?: txt(meta["twitter:site"], MAX_SITE_NAME),
            imageUrl = image,
            imageAlt = txt(meta["og:image:alt"] ?: meta["twitter:image:alt"], MAX_TITLE),
            faviconUrl = abs(icon) ?: abs(appleIcon) ?: UrlSafety.resolve(pageUrl, "/favicon.ico"),
            canonicalUrl = abs(meta["og:url"]) ?: abs(canonical),
            type = txt(meta["og:type"], 60) ?: txt(meta["twitter:card"], 60),
            oembedUrl = abs(oembed),
            titleSource = source,
            metaCsp = metaCsp.toList(),
        )
    }

    /** Reads `<name attr=...>` starting at [lt]. Returns null if it is not a well-formed start tag. */
    private fun readTag(s: String, lt: Int): Tag? {
        var i = lt + 1
        val n = s.length
        val nameStart = i
        while (i < n && (s[i].isLetterOrDigit() || s[i] == '-' || s[i] == ':')) i++
        if (i == nameStart) return null
        val name = s.substring(nameStart, i).lowercase()
        val attrs = HashMap<String, String>()
        while (i < n) {
            while (i < n && (s[i].isWhitespace() || s[i] == '/')) i++
            if (i >= n) return null
            if (s[i] == '>') return Tag(name, attrs, i + 1)
            val aStart = i
            while (i < n && !s[i].isWhitespace() && s[i] != '=' && s[i] != '>' && s[i] != '/') i++
            val aName = s.substring(aStart, i).lowercase()
            while (i < n && s[i].isWhitespace()) i++
            var value = ""
            if (i < n && s[i] == '=') {
                i++
                while (i < n && s[i].isWhitespace()) i++
                if (i < n && (s[i] == '"' || s[i] == '\'')) {
                    val q = s[i]
                    val close = s.indexOf(q, i + 1)
                    if (close < 0) return null
                    value = s.substring(i + 1, close)
                    i = close + 1
                } else {
                    val vStart = i
                    while (i < n && !s[i].isWhitespace() && s[i] != '>') i++
                    value = s.substring(vStart, i)
                }
            }
            if (aName.isNotEmpty() && aName !in attrs) attrs[aName] = value
        }
        return null
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to "\u00A0",
        "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026",
        "laquo" to "\u00AB", "raquo" to "\u00BB", "lsquo" to "\u2018", "rsquo" to "\u2019",
        "ldquo" to "\u201C", "rdquo" to "\u201D", "copy" to "\u00A9", "reg" to "\u00AE", "trade" to "\u2122",
    )
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[a-zA-Z][a-zA-Z0-9]{1,15});")

    /** Decodes numeric and common named character references. Unknown names are left as-is. */
    fun decode(s: String): String = ENTITY.replace(s) { m ->
        val body = m.groupValues[1]
        when {
            body.startsWith("#x") || body.startsWith("#X") ->
                body.substring(2).toIntOrNull(16)?.let(::codePoint) ?: m.value
            body.startsWith("#") -> body.substring(1).toIntOrNull()?.let(::codePoint) ?: m.value
            else -> NAMED[body.lowercase()] ?: m.value
        }
    }

    private fun codePoint(cp: Int): String? =
        if (cp == 0 || cp > 0x10FFFF || cp in 0xD800..0xDFFF) "\uFFFD" else String(Character.toChars(cp))

    /** Collapses whitespace, drops control characters, and caps length (with an ellipsis). */
    fun clean(s: String, max: Int): String {
        val collapsed = s.filter { it == ' ' || it == '\n' || it == '\t' || it.code >= 0x20 && it.code != 0x7F }
            .replace(Regex("\\s+"), " ").trim()
        return if (collapsed.length <= max) collapsed else collapsed.take(max - 1).trimEnd() + "\u2026"
    }
}
