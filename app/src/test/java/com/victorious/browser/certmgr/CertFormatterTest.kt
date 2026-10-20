package com.victorious.browser.certmgr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertFormatterTest {

    private val sample = "hello world".toByteArray(Charsets.US_ASCII)

    @Test fun pemHasHeaderFooterAndWrapping() {
        val big = ByteArray(200) { it.toByte() }
        val pem = CertFormatter.toPem(big, "CERTIFICATE")
        assertTrue(pem.startsWith("-----BEGIN CERTIFICATE-----\n"))
        assertTrue(pem.trimEnd().endsWith("-----END CERTIFICATE-----"))
        // base64 body lines (excluding header/footer) must be <= 64 chars
        pem.lines().filter { it.isNotEmpty() && !it.startsWith("-----") }
            .forEach { assertTrue("line too long: ${it.length}", it.length <= 64) }
    }

    @Test fun pemRoundTripsThroughBase64() {
        val pem = CertFormatter.toPem(sample)
        val body = pem.lines().filter { it.isNotEmpty() && !it.startsWith("-----") }.joinToString("")
        val decoded = java.util.Base64.getDecoder().decode(body)
        assertEquals("hello world", String(decoded, Charsets.US_ASCII))
    }

    @Test fun fingerprintIsColonHexUppercase() {
        val fp = CertFormatter.sha256Fingerprint(sample)
        // 32 bytes -> 32 pairs separated by 31 colons
        val parts = fp.split(":")
        assertEquals(32, parts.size)
        parts.forEach { assertTrue(it.matches(Regex("[0-9A-F]{2}"))) }
    }

    @Test fun knownSha256Vector() {
        // SHA-256("") known digest
        val fp = CertFormatter.sha256Hex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", fp)
    }

    @Test fun shortFingerprintHasNBytes() {
        val s = CertFormatter.shortFingerprint(sample, 4)
        assertEquals(4, s.split(":").size)
    }

    @Test fun safeBaseNameStripsIllegalAndFallsBack() {
        assertEquals("victorious-ca", CertFormatter.safeBaseName("   "))
        assertEquals("my-ca-1", CertFormatter.safeBaseName("my ca/1"))
        assertFalse(CertFormatter.safeBaseName("../../etc/passwd").contains("/"))
    }
}
