package com.victorious.browser.scanner

/**
 * Small, well-labeled payload sets for the opt-in active scanner.
 *
 * These are intentionally minimal, benign probe strings used to *detect*
 * reflection/error/timing behavior on the user's own authorized targets — not
 * weaponized exploits. Each payload carries a human label and the detection
 * strategy it drives. Pure data; unit-tested in [ScanPayloadsTest].
 *
 * Payload ideas are common public knowledge (OWASP Testing Guide categories).
 * See NOTICE for attribution of referenced open resources.
 */
object ScanPayloads {

    enum class Category { XSS, SQLI, SSRF }

    enum class Detect { REFLECTION, SQL_ERROR, BOOLEAN, TIMING, OOB }

    data class Payload(
        val category: Category,
        val value: String,
        val label: String,
        val detect: Detect,
        /** Unique marker embedded so reflection can be matched unambiguously. */
        val marker: String? = null,
    )

    /** A canary token the UI can regenerate per run for unambiguous reflection. */
    fun xssProbes(canary: String): List<Payload> = listOf(
        Payload(Category.XSS, "$canary<svg/onload=1>", "XSS reflection canary (tag break)", Detect.REFLECTION, canary),
        Payload(Category.XSS, "\"$canary'><", "XSS attribute/tag break canary", Detect.REFLECTION, canary),
    )

    /** Error-based + boolean + time-based SQLi heuristic probes. */
    val sqliProbes: List<Payload> = listOf(
        Payload(Category.SQLI, "'", "Single quote (error-based)", Detect.SQL_ERROR),
        Payload(Category.SQLI, "\"", "Double quote (error-based)", Detect.SQL_ERROR),
        Payload(Category.SQLI, "' AND '1'='1", "Boolean TRUE", Detect.BOOLEAN),
        Payload(Category.SQLI, "' AND '1'='2", "Boolean FALSE", Detect.BOOLEAN),
        // Time-based: label only; the engine measures elapsed time vs baseline.
        Payload(Category.SQLI, "' AND SLEEP(5)-- -", "Time-based (MySQL SLEEP)", Detect.TIMING),
        Payload(Category.SQLI, "'; WAITFOR DELAY '0:0:5'-- -", "Time-based (MSSQL WAITFOR)", Detect.TIMING),
    )

    /**
     * SSRF indicator probes. Without a configured OOB collaborator these only
     * drive response-shape heuristics (connection errors, metadata strings);
     * when a collaborator domain is set, [Collaborator] supplies OOB payloads.
     */
    fun ssrfProbes(collaboratorHost: String?): List<Payload> {
        val base = mutableListOf(
            Payload(Category.SSRF, "http://127.0.0.1/", "Loopback (response diff)", Detect.REFLECTION),
            Payload(Category.SSRF, "http://169.254.169.254/latest/meta-data/", "Cloud metadata probe", Detect.REFLECTION),
        )
        if (!collaboratorHost.isNullOrBlank()) {
            base += Payload(Category.SSRF, "http://$collaboratorHost/", "OOB collaborator callback", Detect.OOB)
        }
        return base
    }

    fun all(canary: String, collaboratorHost: String?): List<Payload> =
        xssProbes(canary) + sqliProbes + ssrfProbes(collaboratorHost)
}
