package com.victorious.browser.scanner

import com.victorious.browser.inspect.EditableRequest
import com.victorious.browser.inspect.ReplayClient
import java.util.concurrent.Executors

/**
 * Opt-in, per-request active scanner. Replays a selected request with a small,
 * labeled payload set across each insertion point and classifies the responses
 * into [Finding]s with evidence.
 *
 * SAFEGUARDS:
 *  - [run] refuses unless the request's host is in [ScannerConfig] scope.
 *  - It only ever runs when the user invokes it explicitly (a Run action in the
 *    UI); it is never wired into live browsing/capture.
 *
 * The pure classification ([evaluate]) is unit-tested in [ActiveScannerTest];
 * the orchestration here reuses [ReplayClient] for the actual replay.
 */
object ActiveScanner {

    private val executor = Executors.newSingleThreadExecutor()

    data class Outcome(
        val status: Int?,
        val body: String?,
        val elapsedMs: Long,
        val error: String?,
    )

    data class Progress(val done: Int, val total: Int, val note: String)

    class ScopeViolation(message: String) : Exception(message)

    /**
     * Pure classification of a single non-timing/non-boolean probe outcome.
     * Returns a [Finding] when the evidence supports one, else null.
     */
    fun evaluate(
        point: InsertionPoints.Point,
        payload: ScanPayloads.Payload,
        url: String,
        baseline: Outcome,
        outcome: Outcome,
    ): Finding? {
        return when (payload.detect) {
            ScanPayloads.Detect.REFLECTION -> {
                val marker = payload.marker ?: payload.value
                if (ResponseDiff.markerReflected(marker, outcome.body) ||
                    ResponseDiff.reflected(payload.value, outcome.body)
                ) {
                    Finding(
                        category = payload.category,
                        insertionPoint = point,
                        payloadLabel = payload.label,
                        payloadValue = payload.value,
                        confidence = if (payload.category == ScanPayloads.Category.XSS) {
                            Finding.Confidence.LIKELY
                        } else {
                            Finding.Confidence.GUESSING
                        },
                        evidence = "Payload reflected verbatim in response body (status ${outcome.status}).",
                        url = url,
                    )
                } else {
                    null
                }
            }
            ScanPayloads.Detect.SQL_ERROR -> {
                val db = ResponseDiff.sqlErrorMatch(outcome.body)
                if (db != null && ResponseDiff.sqlErrorMatch(baseline.body) == null) {
                    Finding(
                        category = ScanPayloads.Category.SQLI,
                        insertionPoint = point,
                        payloadLabel = payload.label,
                        payloadValue = payload.value,
                        confidence = Finding.Confidence.LIKELY,
                        evidence = "$db error signature appeared after injecting ${payload.value} (not in baseline).",
                        url = url,
                    )
                } else {
                    null
                }
            }
            ScanPayloads.Detect.TIMING -> {
                if (ResponseDiff.timingSuspicious(baseline.elapsedMs, outcome.elapsedMs)) {
                    Finding(
                        category = ScanPayloads.Category.SQLI,
                        insertionPoint = point,
                        payloadLabel = payload.label,
                        payloadValue = payload.value,
                        confidence = Finding.Confidence.GUESSING,
                        evidence = "Response delayed ${outcome.elapsedMs}ms vs baseline ${baseline.elapsedMs}ms with time-based payload.",
                        url = url,
                    )
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /** Evaluate a boolean-based SQLi pair. */
    fun evaluateBoolean(
        point: InsertionPoints.Point,
        url: String,
        baseline: Outcome,
        trueResp: Outcome,
        falseResp: Outcome,
    ): Finding? {
        if (ResponseDiff.booleanDiffSuspicious(baseline.body, trueResp.body, falseResp.body)) {
            return Finding(
                category = ScanPayloads.Category.SQLI,
                insertionPoint = point,
                payloadLabel = "Boolean-based (TRUE vs FALSE)",
                payloadValue = "' AND '1'='1  /  ' AND '1'='2",
                confidence = Finding.Confidence.GUESSING,
                evidence = "TRUE response matched baseline while FALSE response diverged " +
                    "(len base=${baseline.body?.length}, true=${trueResp.body?.length}, false=${falseResp.body?.length}).",
                url = url,
            )
        }
        return null
    }

    private fun send(req: EditableRequest): Outcome {
        val start = System.currentTimeMillis()
        val r = ReplayClient.replay(req, throughProxy = false)
        val elapsed = System.currentTimeMillis() - start
        return Outcome(
            status = r.statusCode,
            body = r.responseBody?.toString(Charsets.UTF_8),
            elapsedMs = elapsed,
            error = r.error,
        )
    }

    /**
     * Run the scan on a background thread. Throws [ScopeViolation] synchronously
     * if the request host is not in scope — the hard gate.
     */
    fun run(
        req: EditableRequest,
        config: ScannerConfig,
        collaborator: Collaborator.Config,
        canary: String,
        onProgress: (Progress) -> Unit,
        onFinding: (Finding) -> Unit,
        onDone: () -> Unit,
    ) {
        if (!config.isInScope(req.url)) {
            throw ScopeViolation("Target host is not in scope. Add it to the allowlist first.")
        }
        executor.execute {
            try {
                val points = InsertionPoints.enumerate(req)
                val collabHost = if (collaborator.configured) {
                    Collaborator.payloadHost(collaborator, canary)
                } else {
                    null
                }
                val payloads = ScanPayloads.all(canary, collabHost)
                val total = maxOf(1, points.size * (payloads.size + 1))
                var done = 0
                val baseline = send(req)
                for (point in points) {
                    // boolean pair
                    val truePayload = ScanPayloads.sqliProbes.first { it.label == "Boolean TRUE" }
                    val falsePayload = ScanPayloads.sqliProbes.first { it.label == "Boolean FALSE" }
                    for (payload in payloads) {
                        onProgress(Progress(done, total, "${point.kind} ${point.name}: ${payload.label}"))
                        if (payload.detect == ScanPayloads.Detect.BOOLEAN) {
                            done++
                            continue
                        }
                        val mutated = InsertionPoints.apply(req, point, payload.value)
                        val outcome = send(mutated)
                        evaluate(point, payload, req.url, baseline, outcome)?.let(onFinding)
                        done++
                    }
                    // Dedicated boolean pair evaluation per point.
                    val tReq = InsertionPoints.apply(req, point, point.originalValue + truePayload.value)
                    val fReq = InsertionPoints.apply(req, point, point.originalValue + falsePayload.value)
                    val tOut = send(tReq)
                    val fOut = send(fReq)
                    evaluateBoolean(point, req.url, baseline, tOut, fOut)?.let(onFinding)
                }
                onProgress(Progress(total, total, "done"))
            } finally {
                onDone()
            }
        }
    }
}
