package com.victorious.browser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveScannerTest {
    private val point = InsertionPoints.Point(InsertionPoints.Kind.QUERY, "q", "x")
    private fun outcome(body: String?, status: Int? = 200, ms: Long = 100) =
        ActiveScanner.Outcome(status, body, ms, null)

    @Test fun evaluate_reflectionXss_findsLikely() {
        val payload = ScanPayloads.xssProbes("vz123").first()
        val f = ActiveScanner.evaluate(
            point, payload, "https://e.com/?q=x",
            baseline = outcome("clean"),
            outcome = outcome("<p>vz123<svg/onload=1></p>"),
        )
        assertNotNull(f)
        assertEquals(ScanPayloads.Category.XSS, f!!.category)
        assertEquals(Finding.Confidence.LIKELY, f.confidence)
    }

    @Test fun evaluate_reflection_noMatch_null() {
        val payload = ScanPayloads.xssProbes("vz123").first()
        val f = ActiveScanner.evaluate(point, payload, "u", outcome("clean"), outcome("still clean"))
        assertNull(f)
    }

    @Test fun evaluate_sqlError_onlyWhenNotInBaseline() {
        val payload = ScanPayloads.sqliProbes.first { it.detect == ScanPayloads.Detect.SQL_ERROR }
        val mysqlErr = "You have an error in your SQL syntax; check the MySQL manual"
        val found = ActiveScanner.evaluate(point, payload, "u", outcome("clean"), outcome(mysqlErr))
        assertNotNull(found)
        assertEquals(ScanPayloads.Category.SQLI, found!!.category)
        // baseline already has error -> no finding
        val none = ActiveScanner.evaluate(point, payload, "u", outcome(mysqlErr), outcome(mysqlErr))
        assertNull(none)
    }

    @Test fun evaluate_timing_guessing() {
        val payload = ScanPayloads.sqliProbes.first { it.detect == ScanPayloads.Detect.TIMING }
        val f = ActiveScanner.evaluate(point, payload, "u", outcome("ok", ms = 100), outcome("ok", ms = 5500))
        assertNotNull(f)
        assertEquals(Finding.Confidence.GUESSING, f!!.confidence)
    }

    @Test fun evaluateBoolean_detectsDivergence() {
        val base = "x".repeat(1000)
        val f = ActiveScanner.evaluateBoolean(
            point, "u",
            outcome(base), outcome("x".repeat(1000)), outcome("x".repeat(100)),
        )
        assertNotNull(f)
        assertEquals(ScanPayloads.Category.SQLI, f!!.category)
    }
}
