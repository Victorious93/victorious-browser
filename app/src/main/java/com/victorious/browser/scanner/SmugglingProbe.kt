package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest

/**
 * HTTP request-smuggling *detection* heuristics (CL.TE / TE.CL).
 *
 * DETECTION ONLY. This builds requests that set conflicting Content-Length /
 * Transfer-Encoding framing and compares response timing/status against a clean
 * baseline. A divergence is reported as a HEURISTIC GUESS, never a confirmed
 * vulnerability — many results are false positives, and the underlying transport
 * ([com.victorious.browser.inspect.ReplayClient]'s HttpURLConnection) may rewrite
 * framing, which the UI states plainly.
 *
 * The request-construction and verdict logic are pure and unit-tested in
 * [SmugglingProbeTest].
 */
object SmugglingProbe {

    enum class Technique { CL_TE, TE_CL }

    /**
     * Build a probe request for [technique]. The body is crafted so that a
     * front-end/back-end framing disagreement tends to cause the back-end to wait
     * for more bytes (timeout) on a vulnerable path.
     */
    fun buildProbe(base: EditableRequest, technique: Technique): EditableRequest {
        val smuggled = "0\r\n\r\nG"
        val headers = base.headers.filterNot {
            it.name.equals("Content-Length", true) || it.name.equals("Transfer-Encoding", true)
        }.toMutableList()
        val body: String
        when (technique) {
            Technique.CL_TE -> {
                // Front-end uses CL, back-end uses TE.
                headers += CapturedHeader("Content-Length", "4")
                headers += CapturedHeader("Transfer-Encoding", "chunked")
                body = "1\r\nA\r\n0\r\n\r\n"
            }
            Technique.TE_CL -> {
                // Front-end uses TE, back-end uses CL.
                headers += CapturedHeader("Transfer-Encoding", "chunked")
                headers += CapturedHeader("Content-Length", smuggled.length.toString())
                body = smuggled
            }
        }
        val method = if (base.method.equals("GET", true)) "POST" else base.method
        return base.copy(method = method, headers = headers, body = body.toByteArray(Charsets.UTF_8))
    }

    data class Verdict(
        val technique: Technique,
        val suspicious: Boolean,
        val reason: String,
        /** Always true: this engine never confirms, only guesses. */
        val heuristic: Boolean = true,
    )

    /**
     * Interpret a probe outcome vs baseline. A notable timing gap, a timeout on
     * the probe, or a server-side framing error (400/408/501) counts as a
     * suspicious signal worth manual review.
     */
    fun interpret(
        technique: Technique,
        baselineMs: Long,
        probeMs: Long,
        probeTimedOut: Boolean,
        baselineStatus: Int?,
        probeStatus: Int?,
        delayThresholdMs: Long = 5_000,
    ): Verdict {
        if (probeTimedOut) {
            return Verdict(technique, true, "Probe timed out while baseline responded — possible back-end wait on smuggled framing.")
        }
        val framingError = probeStatus != null && probeStatus in setOf(400, 408, 501) && probeStatus != baselineStatus
        if (framingError) {
            return Verdict(technique, true, "Probe returned $probeStatus (framing error) vs baseline $baselineStatus.")
        }
        if (probeMs - baselineMs >= delayThresholdMs) {
            return Verdict(technique, true, "Probe was ${probeMs - baselineMs}ms slower than baseline (>= ${delayThresholdMs}ms).")
        }
        return Verdict(technique, false, "No timing/status divergence observed (not evidence of safety).")
    }
}
