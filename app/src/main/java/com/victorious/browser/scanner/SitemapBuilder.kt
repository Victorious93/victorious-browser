package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedExchange
import java.net.URI

/**
 * Builds a Burp-style site tree from captured HTTP history, optionally extended
 * by a bounded same-origin link crawl.
 *
 * Pure data + pure transforms here (link extraction, dedup, tree assembly) so
 * everything is unit-tested in [SitemapBuilderTest]. The network side of the
 * crawl (actually fetching pages) lives in [SitemapCrawler], which feeds HTML
 * bodies back into [extractLinks].
 */
object SitemapBuilder {

    /** One observed endpoint, deduped by method+normalized-URL. */
    data class Endpoint(
        val method: String,
        val url: String,
        val host: String,
        val path: String,
        val statusCode: Int? = null,
        val fromCrawl: Boolean = false,
    )

    /** A node in the rendered site tree. */
    data class SiteNode(
        val label: String,
        val fullUrl: String?,
        val endpoint: Endpoint?,
        val children: List<SiteNode>,
    )

    /** Stable dedup key for an endpoint. */
    fun dedupKey(method: String, url: String): String =
        method.trim().uppercase() + " " + normalizeUrl(url)

    /** Drop fragment, keep query; lowercase host; collapse default ports. */
    fun normalizeUrl(url: String): String {
        return try {
            val u = URI(url)
            val scheme = (u.scheme ?: "http").lowercase()
            val host = (u.host ?: "").lowercase()
            val port = when {
                u.port < 0 -> ""
                scheme == "http" && u.port == 80 -> ""
                scheme == "https" && u.port == 443 -> ""
                else -> ":" + u.port
            }
            val path = u.rawPath?.takeIf { it.isNotEmpty() } ?: "/"
            val q = u.rawQuery?.let { "?$it" } ?: ""
            "$scheme://$host$port$path$q"
        } catch (_: Exception) {
            url.substringBefore('#')
        }
    }

    /**
     * Extract absolute, resolved links from an HTML body relative to [baseUrl].
     * Matches href/src attributes and action targets; ignores javascript:, data:,
     * mailto:, tel: and fragment-only links.
     */
    fun extractLinks(html: String, baseUrl: String): List<String> {
        val attrRegex = Regex(
            "(?:href|src|action)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))",
            RegexOption.IGNORE_CASE,
        )
        val out = LinkedHashSet<String>()
        for (m in attrRegex.findAll(html)) {
            val raw = (m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { m.groupValues[4] }).trim()
            if (raw.isEmpty()) continue
            val lower = raw.lowercase()
            if (lower.startsWith("javascript:") || lower.startsWith("data:") ||
                lower.startsWith("mailto:") || lower.startsWith("tel:") || raw.startsWith("#")
            ) {
                continue
            }
            val resolved = resolve(baseUrl, raw) ?: continue
            if (resolved.startsWith("http://") || resolved.startsWith("https://")) {
                out += resolved
            }
        }
        return out.toList()
    }

    private fun resolve(baseUrl: String, ref: String): String? {
        return try {
            URI(baseUrl).resolve(ref).toString()
        } catch (_: Exception) {
            null
        }
    }

    /** Collapse history into deduped endpoints, optionally scope-filtered. */
    fun endpointsFromHistory(
        exchanges: List<CapturedExchange>,
        scope: Collection<String> = emptyList(),
    ): List<Endpoint> {
        val seen = LinkedHashMap<String, Endpoint>()
        for (ex in exchanges) {
            if (scope.isNotEmpty() && !ScopeMatcher.hostInScope(ex.host, scope)) continue
            val key = dedupKey(ex.method, ex.url)
            if (seen.containsKey(key)) continue
            seen[key] = Endpoint(
                method = ex.method.uppercase(),
                url = ex.url,
                host = ex.host,
                path = pathOf(ex.url),
                statusCode = ex.statusCode,
            )
        }
        return seen.values.toList()
    }

    /** Merge two endpoint lists, deduped (history wins over crawl on collision). */
    fun mergeEndpoints(primary: List<Endpoint>, extra: List<Endpoint>): List<Endpoint> {
        val seen = LinkedHashMap<String, Endpoint>()
        for (e in primary) seen[dedupKey(e.method, e.url)] = e
        for (e in extra) seen.putIfAbsent(dedupKey(e.method, e.url), e)
        return seen.values.toList()
    }

    fun pathOf(url: String): String =
        try {
            URI(url).rawPath?.takeIf { it.isNotEmpty() } ?: "/"
        } catch (_: Exception) {
            "/"
        }

    /** Assemble endpoints into a host → path-segment tree, sorted. */
    fun buildTree(endpoints: List<Endpoint>): List<SiteNode> {
        // host -> mutable tree
        data class Mutable(
            val label: String,
            var fullUrl: String?,
            var endpoint: Endpoint?,
            val children: LinkedHashMap<String, Mutable>,
        )

        val roots = LinkedHashMap<String, Mutable>()
        for (ep in endpoints.sortedWith(compareBy({ it.host }, { it.path }, { it.method }))) {
            val root = roots.getOrPut(ep.host) {
                Mutable(ep.host, null, null, LinkedHashMap())
            }
            val segments = ep.path.trim('/').split('/').filter { it.isNotEmpty() }
            var cursor = root
            val sb = StringBuilder()
            for (seg in segments) {
                sb.append('/').append(seg)
                cursor = cursor.children.getOrPut(seg) {
                    Mutable(seg, null, null, LinkedHashMap())
                }
            }
            // leaf gets a labelled method entry
            val leafLabel = "${ep.method} ${ep.path}" + if (ep.statusCode != null) "  [${ep.statusCode}]" else ""
            cursor.children["\u0000$leafLabel"] = Mutable(leafLabel, ep.url, ep, LinkedHashMap())
        }

        fun convert(m: Mutable): SiteNode = SiteNode(
            label = m.label,
            fullUrl = m.fullUrl,
            endpoint = m.endpoint,
            children = m.children.values.map { convert(it) },
        )
        return roots.values.map { convert(it) }
    }
}
