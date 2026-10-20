package com.victorious.browser.scanner

/**
 * A single scanner finding with the evidence that produced it. All scanner
 * findings are reported with confidence and the concrete evidence (payload,
 * what reflected / which error / timing) so the user can verify manually.
 */
data class Finding(
    val category: ScanPayloads.Category,
    val insertionPoint: InsertionPoints.Point,
    val payloadLabel: String,
    val payloadValue: String,
    val confidence: Confidence,
    val evidence: String,
    val url: String,
) {
    enum class Confidence { CERTAIN, LIKELY, GUESSING }
}
