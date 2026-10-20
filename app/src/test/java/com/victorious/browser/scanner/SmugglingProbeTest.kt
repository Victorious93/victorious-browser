package com.victorious.browser.scanner

import com.victorious.browser.inspect.EditableRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmugglingProbeTest {
    private val base = EditableRequest("GET", "https://example.com/", emptyList())

    @Test fun buildProbe_clte_hasConflictingHeaders() {
        val p = SmugglingProbe.buildProbe(base, SmugglingProbe.Technique.CL_TE)
        assertEquals("POST", p.method)
        assertTrue(p.headers.any { it.name.equals("Content-Length", true) })
        assertTrue(p.headers.any { it.name.equals("Transfer-Encoding", true) && it.value.contains("chunked") })
    }
    @Test fun buildProbe_tecl_hasBoth() {
        val p = SmugglingProbe.buildProbe(base, SmugglingProbe.Technique.TE_CL)
        assertTrue(p.headers.any { it.name.equals("Content-Length", true) })
        assertTrue(p.headers.any { it.name.equals("Transfer-Encoding", true) })
    }
    @Test fun interpret_timeoutSuspicious() {
        val v = SmugglingProbe.interpret(SmugglingProbe.Technique.CL_TE, 100, 100, true, 200, null)
        assertTrue(v.suspicious)
        assertTrue(v.heuristic)
    }
    @Test fun interpret_framingErrorSuspicious() {
        val v = SmugglingProbe.interpret(SmugglingProbe.Technique.TE_CL, 100, 150, false, 200, 400)
        assertTrue(v.suspicious)
    }
    @Test fun interpret_timingGapSuspicious() {
        val v = SmugglingProbe.interpret(SmugglingProbe.Technique.CL_TE, 100, 6000, false, 200, 200)
        assertTrue(v.suspicious)
    }
    @Test fun interpret_noSignal() {
        val v = SmugglingProbe.interpret(SmugglingProbe.Technique.CL_TE, 100, 150, false, 200, 200)
        assertFalse(v.suspicious)
        assertTrue(v.heuristic)
    }
}
