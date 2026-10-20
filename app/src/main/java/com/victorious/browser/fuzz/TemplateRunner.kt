package com.victorious.browser.fuzz

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest
import com.victorious.browser.inspect.ReplayClient
import com.victorious.browser.scanner.ScopeMatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs [FuzzTemplate]s against an in-scope base URL via [ReplayClient].
 *
 * SAFEGUARD: refuses to run unless the base host matches the caller's scope
 * allowlist, and only runs on an explicit [start]. Matcher evaluation is pure
 * ([MatcherEngine]); this runner only sends requests and collects findings.
 */
class TemplateRunner(
    private val baseUrl: String,
    private val templates: List<FuzzTemplate>,
    private val scopeHosts: Collection<String>,
) {
    class OutOfScopeException(val host: String?) :
        IllegalStateException("Base host '$host' is not in the scanner scope allowlist; refusing to run.")

    private val stopped = AtomicBoolean(false)
    @Volatile private var executor: java.util.concurrent.ExecutorService? = null

    val baseHost: String? get() = ScopeMatcher.hostOf(baseUrl)

    fun stop() {
        stopped.set(true)
        executor?.shutdownNow()
    }

    fun start(
        onFinding: (TemplateFinding) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onDone: () -> Unit = {},
    ) {
        val host = baseHost
        if (!ScopeMatcher.hostInScope(host, scopeHosts)) {
            throw OutOfScopeException(host)
        }
        val pool = Executors.newSingleThreadExecutor()
        executor = pool
        val total = templates.sumOf { it.requests.size }
        pool.execute {
            var done = 0
            try {
                for (tpl in templates) {
                    if (stopped.get()) break
                    for (req in tpl.requests) {
                        if (stopped.get()) break
                        val url = joinUrl(baseUrl, req.path)
                        val resp = sendOne(url, req)
                        MatcherEngine.evaluate(tpl, resp, url)?.let(onFinding)
                        onProgress(++done, total)
                    }
                }
            } finally {
                onDone()
            }
        }
    }

    private fun sendOne(url: String, req: TemplateRequest): TemplateResponse {
        val headers = req.headers.map { CapturedHeader(it.key, it.value) }
            .ifEmpty { listOf(CapturedHeader("User-Agent", "VictoriousBrowser-Templates")) }
        val editable = EditableRequest(
            method = req.method,
            url = url,
            headers = headers,
            body = req.body?.toByteArray(Charsets.UTF_8),
        )
        val r = ReplayClient.replay(editable, throughProxy = false)
        val headersText = r.responseHeaders.joinToString("\n") { "${it.name}: ${it.value}" }
        return TemplateResponse(
            status = r.statusCode,
            body = r.responseBody?.toString(Charsets.UTF_8) ?: "",
            headersText = headersText,
        )
    }

    companion object {
        /** Join a scheme://host[/..] base with a template path, avoiding doubled slashes. */
        fun joinUrl(base: String, path: String): String {
            val b = base.trimEnd('/')
            val p = if (path.startsWith("/")) path else "/$path"
            return b + p
        }
    }
}
