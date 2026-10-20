package com.victorious.browser.scanner

import com.victorious.browser.inspect.EditableRequest
import com.victorious.browser.inspect.ReplayClient
import java.util.concurrent.Executors

/**
 * Network orchestration for the header fuzzer and smuggling probe. Both reuse
 * [ReplayClient] and are gated on scope by the caller (the UI checks scope before
 * invoking). The decision logic lives in [HeaderFuzzer] / [SmugglingProbe] and is
 * unit-tested; these runners only send and time requests.
 */
object ScanRunners {

    private val executor = Executors.newSingleThreadExecutor()

    data class Sent(val status: Int?, val body: String?, val elapsedMs: Long, val timedOut: Boolean, val error: String?)

    private fun send(req: EditableRequest): Sent {
        val start = System.currentTimeMillis()
        val r = ReplayClient.replay(req, throughProxy = false)
        val elapsed = System.currentTimeMillis() - start
        val timedOut = r.error?.contains("timed out", ignoreCase = true) == true ||
            r.error?.contains("timeout", ignoreCase = true) == true
        return Sent(r.statusCode, r.responseBody?.toString(Charsets.UTF_8), elapsed, timedOut, r.error)
    }

    fun runHeaderFuzz(
        req: EditableRequest,
        evilHost: String,
        onResult: (List<HeaderFuzzer.Diff>) -> Unit,
    ) {
        executor.execute {
            val baseline = send(req)
            val diffs = HeaderFuzzer.BUILT_IN.map { mut ->
                val mutated = HeaderFuzzer.apply(req, mut, evilHost)
                val out = send(mutated)
                HeaderFuzzer.diff(mut, baseline.status, baseline.body, out.status, out.body)
            }
            onResult(diffs)
        }
    }

    fun runSmuggling(
        req: EditableRequest,
        onResult: (List<SmugglingProbe.Verdict>) -> Unit,
    ) {
        executor.execute {
            val baseline = send(req)
            val verdicts = SmugglingProbe.Technique.entries.map { tech ->
                val probe = SmugglingProbe.buildProbe(req, tech)
                val out = send(probe)
                SmugglingProbe.interpret(
                    technique = tech,
                    baselineMs = baseline.elapsedMs,
                    probeMs = out.elapsedMs,
                    probeTimedOut = out.timedOut,
                    baselineStatus = baseline.status,
                    probeStatus = out.status,
                )
            }
            onResult(verdicts)
        }
    }
}
