package com.victorious.browser.evidence

/**
 * Pure file-name / folder-name helpers for evidence cases. No Android, no I/O.
 *
 * Guards against path traversal and illegal chars so a case name or item title
 * can never escape the case folder or break a ZIP entry.
 */
object EvidenceFilenames {

    private val ILLEGAL = Regex("[^A-Za-z0-9._-]+")

    /** Sanitize an arbitrary label into a safe single path segment. */
    fun sanitize(label: String, fallback: String = "item"): String {
        val cleaned = label.trim()
            .replace(ILLEGAL, "_")
            .trim('_', '.', '-')
        // never allow "." / ".." or empty
        val safe = if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") fallback else cleaned
        return safe.take(100)
    }

    /** Folder name for a new case: "<ts>-<sanitized name>". */
    fun caseFolderName(createdAt: Long, name: String): String =
        "$createdAt-${sanitize(name, "case")}"

    /** PNG screenshot file name inside a case folder. */
    fun screenshotName(timestamp: Long, host: String): String =
        "shot-$timestamp-${sanitize(host, "host")}.png"

    /** ZIP file name for an exported case. */
    fun caseZipName(name: String, exportedAt: Long): String =
        "evidence-${sanitize(name, "case")}-$exportedAt.zip"
}
