package com.victorious.browser.certmgr

import java.security.MessageDigest
import java.util.Base64

/**
 * Pure encoding / formatting helpers for certificate material.
 *
 * No Android, no I/O — just DER→PEM encoding, SHA-256 fingerprint formatting and
 * safe file-name construction. Kept separate from the Android-facing export code
 * so the fiddly byte/string logic can be unit-tested on the JVM.
 */
object CertFormatter {

    /** Standard PEM line length (RFC 7468). */
    private const val PEM_LINE = 64

    /**
     * Encode raw DER bytes as a PEM block with the given [type]
     * (e.g. "CERTIFICATE"). Uses CRLF-free, LF-terminated base64 lines of 64
     * chars, which is what OpenSSL / Android KeyChain both accept.
     */
    fun toPem(der: ByteArray, type: String = "CERTIFICATE"): String {
        val b64 = Base64.getEncoder().encodeToString(der)
        val sb = StringBuilder()
        sb.append("-----BEGIN ").append(type).append("-----\n")
        var i = 0
        while (i < b64.length) {
            val end = minOf(i + PEM_LINE, b64.length)
            sb.append(b64, i, end).append('\n')
            i = end
        }
        sb.append("-----END ").append(type).append("-----\n")
        return sb.toString()
    }

    /**
     * SHA-256 fingerprint of the DER bytes, formatted as uppercase
     * colon-separated hex pairs (the format Android Settings shows), e.g.
     * "AB:CD:...". This is the canonical "CA SHA-256 fingerprint" shown in the UI.
     */
    fun sha256Fingerprint(der: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return digest.joinToString(":") { String.format("%02X", it) }
    }

    /** Same digest as a compact lowercase hex string, no separators. */
    fun sha256Hex(der: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return digest.joinToString("") { String.format("%02x", it) }
    }

    /** First [n] bytes of the fingerprint, for short display labels. */
    fun shortFingerprint(der: ByteArray, bytes: Int = 8): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(der)
        return digest.take(bytes).joinToString(":") { String.format("%02X", it) }
    }

    /**
     * Build a safe base file name (no extension) from a free-text label, keeping
     * only ascii word chars / dash / dot and collapsing runs. Empty → fallback.
     */
    fun safeBaseName(label: String, fallback: String = "victorious-ca"): String {
        val cleaned = label.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.', '_')
        return if (cleaned.isEmpty()) fallback else cleaned.take(80)
    }
}
