package com.victorious.browser.inspect

/**
 * Victorious Browser — render a captured / editable HTTP request as a runnable
 * `curl` command, the way an intercepting proxy's "Copy as cURL" does.
 *
 * Pure (no Android / Netty types) so the quoting and header/body rules are fully
 * unit-tested. Output is a single line, POSIX-shell quoted (safe to paste into
 * bash/zsh). It faithfully reproduces the request — including any auth headers —
 * because the operator is copying their own captured request for replay; it is
 * not an export and is never secret-stripped.
 */
object CurlFormatter {

    /** Headers curl sets itself; emitting a captured value would fight curl. */
    private val DROP_HEADERS = setOf("content-length")

    /** POSIX single-quote a string so the shell passes it through verbatim. */
    private fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    /**
     * Build a `curl` command for [method] [url] with [headers] and optional [body].
     *
     * - The method is emitted with `-X` unless it is GET (and there is no body).
     * - HTTP/2 pseudo-headers (names starting with ":") and Content-Length are skipped.
     * - A non-empty [body] is attached with `--data-raw` (decoded as UTF-8).
     */
    fun toCurl(
        method: String,
        url: String,
        headers: List<CapturedHeader>,
        body: ByteArray? = null,
    ): String {
        val m = method.trim().uppercase().ifEmpty { "GET" }
        val bodyText = body?.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8)
        val hasBody = !bodyText.isNullOrEmpty()

        val sb = StringBuilder("curl")
        // GET with no body needs no -X; anything else is explicit.
        if (m != "GET" || hasBody) sb.append(" -X ").append(shellQuote(m))
        sb.append(' ').append(shellQuote(url))

        for (h in headers) {
            val name = h.name.trim()
            if (name.isEmpty() || name.startsWith(":")) continue
            if (name.lowercase() in DROP_HEADERS) continue
            sb.append(" -H ").append(shellQuote("${h.name}: ${h.value}"))
        }

        if (hasBody) sb.append(" --data-raw ").append(shellQuote(bodyText!!))
        return sb.toString()
    }

    /** Convenience: a [CapturedExchange] from the inspection history. */
    fun toCurl(ex: CapturedExchange): String =
        toCurl(ex.method, ex.url, ex.requestHeaders, ex.requestBody)

    /** Convenience: an [EditableRequest] from the intercept / replay editor. */
    fun toCurl(req: EditableRequest): String =
        toCurl(req.method, req.url, req.headers, req.body)
}
