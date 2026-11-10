package com.victorious.browser.linkembed

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.nio.charset.Charset

/** One HTTP exchange as seen by [BoundedFetcher]. Implementations must NOT follow redirects. */
interface HttpTransport {
    @Throws(IOException::class)
    fun execute(url: String, headers: Map<String, String>): TransportResponse
}

class TransportResponse(
    val status: Int,
    /** Header name → values; names compared case-insensitively. */
    val headers: Map<String, List<String>>,
    val body: InputStream?,
    private val onClose: () -> Unit = {},
) : Closeable {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
    override fun close() {
        runCatching { body?.close() }
        onClose()
    }
}

data class FetchLimits(
    val maxRedirects: Int = 5,
    val maxBytes: Int = 512 * 1024,
    /** Overall deadline across all hops and body reading. */
    val totalTimeoutMs: Long = 15_000,
)

data class FetchResult(
    val requestedUrl: String,
    val finalUrl: String?,
    val status: Int?,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    /** True when the body was cut at [FetchLimits.maxBytes]. */
    val truncated: Boolean,
    /** Every URL visited after the first, in order. */
    val redirects: List<String>,
    val error: String? = null,
) {
    val ok: Boolean get() = error == null && status != null
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
    val contentType: String? get() = header("Content-Type")

    /** Decodes the body using the Content-Type charset (default UTF-8). */
    fun text(): String {
        val cs = contentType?.let { Regex("(?i)charset\\s*=\\s*\"?([\\w.:-]+)").find(it)?.groupValues?.get(1) }
        val charset = runCatching { cs?.let { Charset.forName(it) } }.getOrNull() ?: Charsets.UTF_8
        return String(body, charset)
    }

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Bounded HTTP GET used for every preview / oEmbed / recon / image fetch:
 *  - http(s) only — the start URL and every redirect `Location` go through [UrlSafety];
 *  - redirects followed manually, capped at [FetchLimits.maxRedirects], loops rejected;
 *  - body capped at [FetchLimits.maxBytes] (excess is not read) and an overall deadline;
 *  - [rewrite] applies the user's privacy rewrites (tracking-param strip / HTTPS upgrade)
 *    to the start URL and to each redirect hop.
 * Pure JVM; the transport is injected so tests can script responses.
 */
class BoundedFetcher(
    private val transport: HttpTransport,
    private val limits: FetchLimits = FetchLimits(),
    private val rewrite: (String) -> String = { it },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun fetch(url: String, headers: Map<String, String> = emptyMap(), maxBytes: Int = limits.maxBytes): FetchResult {
        val start = clock()
        val redirects = mutableListOf<String>()
        val first = UrlSafety.check(url)
        if (first is UrlSafety.Result.Rejected) return fail(url, null, redirects, first.reason)
        var current = UrlSafety.sanitize(rewrite((first as UrlSafety.Result.Ok).url))
            ?: return fail(url, null, redirects, "Privacy rewrite produced an unsafe URL")
        val visited = hashSetOf(current)
        while (true) {
            if (clock() - start > limits.totalTimeoutMs) return fail(url, current, redirects, "Timed out")
            val resp = try {
                transport.execute(current, headers)
            } catch (e: Exception) {
                return fail(url, current, redirects, e.message ?: e.javaClass.simpleName)
            }
            resp.use { r ->
                if (r.status in REDIRECT_CODES) {
                    val loc = r.header("Location")
                        ?: return FetchResult(url, current, r.status, r.headers, ByteArray(0), false, redirects.toList(), "Redirect without Location")
                    if (redirects.size >= limits.maxRedirects) {
                        return fail(url, current, redirects, "Too many redirects (limit ${limits.maxRedirects})", r.status, r.headers)
                    }
                    val resolved = UrlSafety.resolve(current, loc)
                        ?: return fail(url, current, redirects, "Redirect to a blocked or malformed URL", r.status, r.headers)
                    val next = UrlSafety.sanitize(rewrite(resolved))
                        ?: return fail(url, current, redirects, "Redirect to a blocked or malformed URL", r.status, r.headers)
                    if (!visited.add(next)) return fail(url, current, redirects, "Redirect loop", r.status, r.headers)
                    redirects += next
                    current = next
                    return@use
                }
                val declared = r.header("Content-Length")?.trim()?.toLongOrNull()
                if (declared != null && declared > maxBytes) {
                    return FetchResult(url, current, r.status, r.headers, ByteArray(0), true, redirects.toList())
                }
                val (bytes, truncated, timedOut) = readBounded(r.body, maxBytes, start)
                return FetchResult(
                    url, current, r.status, r.headers, bytes, truncated, redirects.toList(),
                    if (timedOut) "Timed out while reading the response" else null,
                )
            }
        }
    }

    private fun readBounded(input: InputStream?, maxBytes: Int, start: Long): Triple<ByteArray, Boolean, Boolean> {
        if (input == null) return Triple(ByteArray(0), false, false)
        val out = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buf = ByteArray(8 * 1024)
        try {
            while (true) {
                if (clock() - start > limits.totalTimeoutMs) return Triple(out.toByteArray(), false, true)
                val want = minOf(buf.size, maxBytes + 1 - out.size())
                if (want <= 0) return Triple(out.toByteArray().copyOf(maxBytes), true, false)
                val n = input.read(buf, 0, want)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > maxBytes) return Triple(out.toByteArray().copyOf(maxBytes), true, false)
            }
        } catch (_: IOException) {
            // Return what we have; a partial body is still useful for head metadata.
        }
        return Triple(out.toByteArray(), false, false)
    }

    private fun fail(
        requested: String,
        current: String?,
        redirects: List<String>,
        error: String,
        status: Int? = null,
        headers: Map<String, List<String>> = emptyMap(),
    ) = FetchResult(requested, current, status, headers, ByteArray(0), false, redirects.toList(), error)

    companion object {
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}

/**
 * Real transport over [HttpURLConnection]: no automatic redirects, no HTTP cache, no
 * cookie jar (the app installs no default CookieHandler, so nothing from the WebView
 * jar is sent), bounded connect/read timeouts, and an optional [proxy] — the local
 * inspection proxy when it is running, so previews show up in proxy history too.
 */
class UrlConnectionTransport(
    private val proxy: Proxy? = null,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 10_000,
) : HttpTransport {
    override fun execute(url: String, headers: Map<String, String>): TransportResponse {
        val u = URL(url)
        val conn = (if (proxy != null) u.openConnection(proxy) else u.openConnection()) as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = "GET"
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        val code = conn.responseCode
        val hdrs = conn.headerFields.orEmpty().filterKeys { it != null }.mapKeys { it.key!! }
        val stream = try {
            if (code >= 400) conn.errorStream else conn.inputStream
        } catch (_: IOException) {
            null
        }
        return TransportResponse(code, hdrs, stream) { conn.disconnect() }
    }
}
