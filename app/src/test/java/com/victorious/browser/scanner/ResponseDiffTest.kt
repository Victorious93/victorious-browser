package com.victorious.browser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponseDiffTest {
    @Test fun reflected_detectsSubstring() {
        assertTrue(ResponseDiff.reflected("vz123<svg", "<html>vz123<svg/onload</html>"))
        assertFalse(ResponseDiff.reflected("vz123", "nothing"))
        assertFalse(ResponseDiff.reflected("", "x"))
    }
    @Test fun sqlError_matchesKnownSignatures() {
        assertEquals("MySQL", ResponseDiff.sqlErrorMatch("You have an error in your SQL syntax; check the MySQL manual"))
        assertEquals("PostgreSQL", ResponseDiff.sqlErrorMatch("PostgreSQL query failed: ERROR: syntax"))
        assertEquals("Oracle", ResponseDiff.sqlErrorMatch("ORA-01756: quoted string not properly terminated"))
        assertNull(ResponseDiff.sqlErrorMatch("all good"))
        assertNull(ResponseDiff.sqlErrorMatch(null))
    }
    @Test fun lengthDelta() {
        assertEquals(3, ResponseDiff.lengthDelta("abc", "abcdef"))
        assertEquals(3, ResponseDiff.lengthDelta(null, "abc"))
    }
    @Test fun booleanDiff_true() {
        val base = "x".repeat(1000)
        val t = "x".repeat(1000)
        val f = "x".repeat(200)
        assertTrue(ResponseDiff.booleanDiffSuspicious(base, t, f))
    }
    @Test fun booleanDiff_falseWhenAllSame() {
        val base = "x".repeat(1000)
        assertFalse(ResponseDiff.booleanDiffSuspicious(base, base, base))
    }
    @Test fun timing_suspiciousWhenDelayed() {
        assertTrue(ResponseDiff.timingSuspicious(baselineMs = 100, testMs = 5200))
        assertFalse(ResponseDiff.timingSuspicious(baselineMs = 100, testMs = 300))
    }
    @Test fun timing_notSuspiciousWhenBaselineAlsoSlow() {
        assertFalse(ResponseDiff.timingSuspicious(baselineMs = 5000, testMs = 5300))
    }
}
