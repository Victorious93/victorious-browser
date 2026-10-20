package com.victorious.browser.scanner

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Bounded, same-origin link crawler feeding [SitemapBuilder]. Opt-in: it only
 * runs when the user starts it, stays within the scope allowlist, and is capped
 * by depth and page count. Link extraction itself is pure ([SitemapBuilder]).
 */
object SitemapCrawler {

    private val executor = Executors.newSingleThreadExecutor()

    data class Options(
        val maxDepth: Int = 2,
        val maxPages: Int = 25,
        val timeoutMs: Int = 10_000,
    )

    /**
     * Crawl starting at [seedUrl], only following links whose host is in [scope].
     * Emits discovered endpoints via [onEndpoint] on a worker thread.
     */
    fun crawl(
        seedUrl: String,
        scope: Collection<String>,
        options: Options = Options(),
        onEndpoint: (SitemapBuilder.Endpoint) -> Unit,
        onDone: (visited: Int) -> Unit,
    ) {
        executor.execute {
            val visited = LinkedHashSet<String>()
            val queue = ArrayDeque<Pair<String, Int>>()
            if (ScopeMatcher.inScope(seedUrl, scope)) queue.add(seedUrl to 0)
            var pages = 0
            while (queue.isNotEmpty() && pages < options.maxPages) {
                val (url, depth) = queue.removeFirst()
                val norm = SitemapBuilder.normalizeUrl(url)
                if (!visited.add(norm)) continue
                pages++
                val (status, body) = fetch(url, options.timeoutMs)
                onEndpoint(
                    SitemapBuilder.Endpoint(
                        method = "GET",
                        url = url,
                        host = ScopeMatcher.hostOf(url) ?: "",
                        path = SitemapBuilder.pathOf(url),
                        statusCode = status,
                        fromCrawl = true,
                    ),
                )
                if (body != null && depth < options.maxDepth) {
                    for (link in SitemapBuilder.extractLinks(body, url)) {
                        if (ScopeMatcher.inScope(link, scope) &&
                            !visited.contains(SitemapBuilder.normalizeUrl(link))
                        ) {
                            queue.add(link to depth + 1)
                        }
                    }
                }
            }
            onDone(pages)
        }
    }

    private fun fetch(url: String, timeoutMs: Int): Pair<Int?, String?> {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.requestMethod = "GET"
            val code = conn.responseCode
            val ct = conn.contentType ?: ""
            val body = if (ct.contains("html", ignoreCase = true) || ct.isEmpty()) {
                try {
                    (if (code >= 400) conn.errorStream else conn.inputStream)
                        ?.readBytes()?.toString(Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            conn.disconnect()
            code to body
        } catch (t: Throwable) {
            Log.w("SitemapCrawler", "fetch failed for $url", t)
            null to null
        }
    }
}
