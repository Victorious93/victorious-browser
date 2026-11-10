package com.victorious.browser.linkembed

import org.json.JSONObject
import java.net.URLEncoder

/** A parsed oEmbed (https://oembed.com) JSON response. Text fields are cleaned and capped. */
data class OEmbedData(
    val type: String,
    val title: String? = null,
    val authorName: String? = null,
    val providerName: String? = null,
    val providerUrl: String? = null,
    /** Provider-supplied embed markup. UNTRUSTED: never inject into an app-privileged context. */
    val html: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val thumbnailUrl: String? = null,
    /** For type=photo: the image URL. */
    val photoUrl: String? = null,
)

object OEmbed {
    const val MAX_HTML = 16 * 1024
    private val TYPES = setOf("photo", "video", "link", "rich")

    /**
     * Parses an oEmbed JSON body. Returns null for anything that is not a JSON object
     * with a valid `type` (and `version` 1.0 when present). URLs are http(s)-checked and
     * resolved against [endpointUrl]; `html` is kept verbatim (capped) but is untrusted.
     */
    fun parse(json: String, endpointUrl: String): OEmbedData? {
        val obj = try { JSONObject(json.trim()) } catch (_: Exception) { return null }
        val type = obj.optString("type", "").lowercase().trim()
        if (type !in TYPES) return null
        val version = obj.opt("version")?.toString()?.trim()
        if (version != null && version.isNotEmpty() && version != "1.0" && version != "1") return null
        fun str(key: String, max: Int): String? =
            obj.optString(key, "").takeIf { it.isNotBlank() && !obj.isNull(key) }?.let { HtmlMetadataParser.clean(it, max) }
        fun url(key: String): String? = obj.optString(key, "").takeIf { !obj.isNull(key) }?.let { UrlSafety.resolve(endpointUrl, it) }
        fun dim(key: String): Int? = obj.opt(key)?.toString()?.trim()?.toDoubleOrNull()?.toInt()?.takeIf { it in 1..10_000 }
        val html = obj.optString("html", "").takeIf { it.isNotBlank() && !obj.isNull("html") }
            ?.let { if (it.length > MAX_HTML) null else it }
        return OEmbedData(
            type = type,
            title = str("title", HtmlMetadataParser.MAX_TITLE),
            authorName = str("author_name", 200),
            providerName = str("provider_name", HtmlMetadataParser.MAX_SITE_NAME),
            providerUrl = url("provider_url"),
            html = if (type == "video" || type == "rich") html else null,
            width = dim("width"),
            height = dim("height"),
            thumbnailUrl = url("thumbnail_url"),
            photoUrl = if (type == "photo") url("url") else null,
        )
    }

    /**
     * A built-in endpoint for a few well-known providers whose pages are too heavy to
     * scan for the discovery `<link>` within our size cap. Returns null when unknown.
     */
    fun knownEndpoint(pageUrl: String): String? {
        val host = UrlSafety.hostOf(pageUrl) ?: return null
        val enc = URLEncoder.encode(pageUrl, "UTF-8")
        return when {
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ->
                "https://www.youtube.com/oembed?format=json&url=$enc"
            host == "vimeo.com" || host.endsWith(".vimeo.com") ->
                "https://vimeo.com/api/oembed.json?url=$enc"
            else -> null
        }
    }

    private val IFRAME_RE = Regex("""(?is)^\s*<iframe\b([^>]*)>\s*</iframe>\s*$""")
    private val SRC_RE = Regex("""(?is)\bsrc\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""")

    /**
     * If provider HTML is exactly one `<iframe ...></iframe>` (the YouTube / Vimeo shape),
     * returns its `src` when it is a safe https URL. Anything else (scripts, multiple
     * elements) returns null and must be rendered in an opaque-origin sandbox instead.
     */
    fun singleIframeSrc(html: String?): String? {
        val m = IFRAME_RE.find(html ?: return null) ?: return null
        val src = SRC_RE.find(m.groupValues[1]) ?: return null
        val raw = src.groupValues[2].ifEmpty { src.groupValues[3] }.ifEmpty { src.groupValues[4] }
        val url = UrlSafety.sanitize(HtmlMetadataParser.decode(raw)) ?: return null
        return url.takeIf { it.startsWith("https://") }
    }
}
