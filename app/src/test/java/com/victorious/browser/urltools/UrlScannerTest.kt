package com.victorious.browser.urltools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlScannerTest {
    @Test
    fun interpretsUrlHausHit() {
        val verdict = UrlScanner.interpretUrlHausJson(
            """{"query_status":"ok","url_status":"online","threat":"malware_download"}""",
        )
        assertEquals("URLhaus", verdict.source)
        assertEquals(true, verdict.malicious)
        assertTrue(verdict.detail.contains("malware_download"))
    }

    @Test
    fun interpretsUrlHausMiss() {
        val verdict = UrlScanner.interpretUrlHausJson("""{"query_status":"no_results"}""")
        assertEquals(false, verdict.malicious)
    }

    @Test
    fun interpretsUrlHausInvalidUrlAsInconclusive() {
        val verdict = UrlScanner.interpretUrlHausJson("""{"query_status":"invalid_url"}""")
        assertEquals(null, verdict.malicious)
    }

    // The next two bodies are captured verbatim from live `curl` calls against
    // urlhaus-api.abuse.ch in this session (see UrlScanner's doc), not guessed.

    @Test
    fun interpretsUrlHausMissingAuthKeyResponse() {
        val verdict = UrlScanner.interpretUrlHausJson("""{"error": "Unauthorized"}""")
        assertEquals(null, verdict.malicious)
        assertTrue(verdict.detail.contains("Unauthorized"))
    }

    @Test
    fun interpretsUrlHausUnknownAuthKeyResponse() {
        val verdict = UrlScanner.interpretUrlHausJson("""{"query_status": "unknown_auth_key"}""")
        assertEquals(null, verdict.malicious)
        assertTrue(verdict.detail.contains("rejected"))
    }

    @Test
    fun interpretsVirusTotalPendingAnalysis() {
        val result = UrlScanner.interpretVirusTotalAnalysisJson(
            """{"data":{"attributes":{"status":"queued"}}}""",
        )
        assertEquals(UrlScanner.VtAnalysisResult.Pending, result)
    }

    @Test
    fun interpretsVirusTotalCompletedAnalysis() {
        val result = UrlScanner.interpretVirusTotalAnalysisJson(
            """{"data":{"attributes":{"status":"completed","stats":{"malicious":2,"suspicious":1,"harmless":60}}}}""",
        )
        assertEquals(UrlScanner.VtAnalysisResult.Completed(malicious = 2, suspicious = 1, harmless = 60), result)
    }
}
