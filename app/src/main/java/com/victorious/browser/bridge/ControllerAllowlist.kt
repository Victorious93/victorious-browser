package com.victorious.browser.bridge

/**
 * Controller allowlist for the fleet control bridge.
 *
 * Package: [CONTROLLER_PACKAGE] only. The fleet dropped any
 * `ai.droidcommand.app.debug` pin — do NOT introduce that package.
 *
 * Cert fingerprints are fleet-provided controller signing certs (DEBUG + RELEASE)
 * used to verify the CALLER. The RELEASE pin is droidcommand-AI's controller
 * release cert — not this app's own signing key.
 *
 * Comparison format: uppercase hex, colon-separated (see [normalize] /
 * [CallerVerifier] which emit `%02X`-colon SHA-256).
 */
object ControllerAllowlist {
    const val CONTROLLER_PACKAGE = "ai.droidcommand.app"

    /** Fleet-provided DEBUG controller signing-cert SHA-256. */
    const val DEBUG_CERT_SHA256 =
        "88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D"

    /** Fleet-provided RELEASE controller signing-cert SHA-256 (caller verification only). */
    const val RELEASE_CERT_SHA256 =
        "9E:A7:B3:14:94:5B:0A:62:97:15:6F:F7:06:E7:0C:0A:B1:B7:24:DE:06:D5:09:9C:A5:12:8A:B4:43:F7:2D:8D"

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
