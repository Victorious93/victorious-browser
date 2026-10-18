package com.victorious.browser.bridge

/**
 * Controller allowlist for the fleet control bridge.
 *
 * Package: [CONTROLLER_PACKAGE] only. The fleet dropped any
 * `ai.droidcommand.app.debug` pin — do NOT introduce that package.
 *
 * Cert fingerprints below are PLACEHOLDERS: fill exact fleet-provided
 * SHA-256 fingerprints. Middles are elided placeholders (00:…); ends match
 * the fleet-published prefixes/suffixes (DEBUG `88:00:51:A6:…:C8:4D`,
 * RELEASE `9E:A7:…:2D:8D`).
 */
object ControllerAllowlist {
    const val CONTROLLER_PACKAGE = "ai.droidcommand.app"

    /**
     * FILL exact fleet-provided DEBUG signing-cert SHA-256.
     * Placeholder middle — starts 88:00:51:A6, ends C8:4D.
     */
    const val DEBUG_CERT_SHA256 =
        "88:00:51:A6:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:C8:4D"

    /**
     * FILL exact fleet-provided RELEASE signing-cert SHA-256.
     * Placeholder middle — starts 9E:A7, ends 2D:8D.
     */
    const val RELEASE_CERT_SHA256 =
        "9E:A7:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:00:2D:8D"

    val PINNED_CERTS: Set<String> = setOf(
        normalize(DEBUG_CERT_SHA256),
        normalize(RELEASE_CERT_SHA256),
    )

    fun normalize(fp: String): String =
        fp.uppercase().replace(" ", "")

    fun isAllowedPackage(pkg: String): Boolean = pkg == CONTROLLER_PACKAGE

    fun isAllowedCert(sha256ColonHex: String): Boolean =
        normalize(sha256ColonHex) in PINNED_CERTS
}
