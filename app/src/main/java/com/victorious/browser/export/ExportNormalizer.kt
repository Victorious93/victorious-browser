package com.victorious.browser.export

import com.victorious.browser.inspect.CapturedExchange
import com.victorious.browser.scanner.Finding
import java.net.URI

/**
 * Pure normalization: turn captured exchanges and scanner/recon findings into a
 * [FleetExportPayload], stripping every secret ([SecretStripper]) along the way.
 *
 * All functions here are side-effect free and unit-tested. Nothing in this file
 * touches Android, the network, or disk.
 */
object ExportNormalizer {

    /** Extract the host of a URL, or null if it can't be parsed. */
    fun hostOf(url: String): String? =
        try {
            val h = URI(url).host
            if (h.isNullOrBlank()) null else h
        } catch (_: Exception) {
            null
        }

    /** Convert one captured exchange to a secret-stripped [ExportEndpoint]. */
    fun endpointOf(ex: CapturedExchange): ExportEndpoint {
        val cleanUrl = SecretStripper.stripUrlQuery(ex.url)
        val params = SecretStripper.stripParams(SecretStripper.parseQuery(ex.url))
        val headers = SecretStripper.stripHeaders(
            // First value wins for duplicate header names.
            LinkedHashMap<String, String>().apply {
                ex.requestHeaders.forEach { if (!containsKey(it.name)) put(it.name, it.value) }
            },
        )
        return ExportEndpoint(
            method = ex.method,
            url = cleanUrl,
            params = params,
            headers = headers,
            statusCode = ex.statusCode,
        )
    }

    /** Convert a scanner [Finding] to a generic [ExportFinding]. */
    fun findingOf(f: Finding): ExportFinding = ExportFinding(
        title = "${f.category} (${f.payloadLabel})",
        category = f.category.name,
        severity = f.confidence.name,
        url = SecretStripper.stripUrlQuery(f.url),
        evidence = f.evidence,
    )

    /**
     * Build a complete export payload. [extraTargets] lets callers seed scope
     * hosts; the hosts of every endpoint/finding are merged in and de-duplicated.
     */
    fun buildPayload(
        exchanges: List<CapturedExchange> = emptyList(),
        findings: List<Finding> = emptyList(),
        extraTargets: List<String> = emptyList(),
        extraFindings: List<ExportFinding> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): FleetExportPayload {
        val endpoints = exchanges.map { endpointOf(it) }
        val mappedFindings = findings.map { findingOf(it) } + extraFindings
        val targets = LinkedHashSet<String>()
        extraTargets.forEach { t -> t.trim().takeIf { it.isNotEmpty() }?.let { targets.add(it) } }
        endpoints.forEach { ep -> hostOf(ep.url)?.let { targets.add(it) } }
        mappedFindings.forEach { f -> hostOf(f.url)?.let { targets.add(it) } }
        return FleetExportPayload(
            targets = targets.toList(),
            endpoints = endpoints,
            findings = mappedFindings,
            exportedAt = now,
        )
    }
}
