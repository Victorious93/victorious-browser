package com.victorious.browser.fuzz

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest
import com.victorious.browser.inspect.ReplayClient
import com.victorious.browser.scanner.ScopeMatcher
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Network orchestration for the directory / endpoint brute-forcer.
 *
 * Reuses [ReplayClient] for every request, exactly like [com.victorious.browser.scanner.ScanRunners].
 * SAFEGUARD: the run refuses to start unless the target host matches the caller's
 * [ScopeMatcher] allowlist (ScannerConfig scope hosts), and it only runs on an
 * explicit [start] call — never on browsing traffic.
 *
 * Decision logic (URL building, filters, dedup) lives in [FuzzLogic] and is
 * unit-tested; this runner only sends, meters, and throttles.
 */
class DirBruteRunner(
    private val baseUrl: String,
    private val words: List<String>,
    private val extensions: List<String>,
    private val filters: FuzzLogic.FilterSpec,
    private val scopeHosts: Collection<String>,
    private val method: String = "GET",
    concurrency: Int = DEFAULT_CONCURRENCY,
) {
    /** Thrown by [start] when the target host is not in the scope allowlist. */
    class OutOfScopeException(val host: String?) :
        IllegalStateException("Target host '$host' is not in the scanner scope allowlist; refusing to run.")

    private val concurrency = concurrency.coerceIn(1, MAX_CONCURRENCY)
    private val stopped = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val completed = AtomicInteger(0)

    @Volatile private var executor: java.util.concurrent.ExecutorService? = null

    val totalTargets: Int by lazy { FuzzLogic.targets(baseUrl, words, extensions).size }
    val completedCount: Int get() = completed.get()
    val isStopped: Boolean get() = stopped.get()
    val isPaused: Boolean get() = paused.get()

    fun pause() { paused.set(true) }
    fun resume() { paused.set(false) }
    fun stop() {
        stopped.set(true)
        executor?.shutdownNow()
    }

    /** The host this run would hit, for UI display / scope errors. */
    val targetHost: String? get() = ScopeMatcher.hostOf(effectiveSampleUrl())

    private fun effectiveSampleUrl(): String =
        if (FuzzLogic.hasMarker(baseUrl)) baseUrl.replace(FuzzLogic.MARKER, "") else baseUrl

    /**
     * Start the run. [onHit] fires on a worker thread for every result that
     * passes the filters; [onProgress] fires per completed request; [onDone]
     * fires once when the run finishes or is stopped. Throws [OutOfScopeException]
     * synchronously if the target is out of scope.
     */
    fun start(
        onHit: (FuzzLogic.Hit) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        onDone: () -> Unit = {},
    ) {
        val host = targetHost
        if (!ScopeMatcher.hostInScope(host, scopeHosts)) {
            throw OutOfScopeException(host)
        }
        val targets = FuzzLogic.targets(baseUrl, words, extensions)
        val total = targets.size
        val pool = Executors.newFixedThreadPool(concurrency)
        executor = pool
        val gate = Semaphore(concurrency)
        Thread {
            try {
                for (url in targets) {
                    if (stopped.get()) break
                    while (paused.get() && !stopped.get()) Thread.sleep(100)
                    if (stopped.get()) break
                    gate.acquire()
                    if (stopped.get()) { gate.release(); break }
                    pool.execute {
                        try {
                            if (stopped.get()) return@execute
                            val meta = sendOne(url)
                            if (FuzzLogic.accept(meta, filters)) {
                                onHit(
                                    FuzzLogic.Hit(
                                        path = url,
                                        status = meta.status,
                                        size = meta.size,
                                        words = meta.words,
                                        lines = meta.lines,
                                    ),
                                )
                            }
                        } finally {
                            onProgress(completed.incrementAndGet(), total)
                            gate.release()
                        }
                    }
                }
                pool.shutdown()
                while (!pool.isTerminated && !stopped.get()) {
                    if (!pool.awaitTermination(200, java.util.concurrent.TimeUnit.MILLISECONDS)) continue
                }
            } catch (_: InterruptedException) {
                // stop() interrupted us; fall through to onDone.
            } finally {
                onDone()
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun sendOne(url: String): FuzzLogic.RespMeta {
        val req = EditableRequest(
            method = method,
            url = url,
            headers = listOf(CapturedHeader("User-Agent", "VictoriousBrowser-Fuzz")),
            body = null,
        )
        val r = ReplayClient.replay(req, throughProxy = false)
        val body = r.responseBody?.toString(Charsets.UTF_8)
        return FuzzLogic.metaOf(r.statusCode, body)
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 10
        const val MAX_CONCURRENCY = 50
    }
}
