package com.victorious.browser.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanPayloadsTest {
    @Test fun xssProbes_embedCanary() {
        val probes = ScanPayloads.xssProbes("vz999")
        assertTrue(probes.isNotEmpty())
        assertTrue(probes.all { it.value.contains("vz999") })
        assertTrue(probes.all { it.marker == "vz999" })
    }
    @Test fun sqli_hasErrorBooleanTiming() {
        val d = ScanPayloads.sqliProbes.map { it.detect }.toSet()
        assertTrue(d.contains(ScanPayloads.Detect.SQL_ERROR))
        assertTrue(d.contains(ScanPayloads.Detect.BOOLEAN))
        assertTrue(d.contains(ScanPayloads.Detect.TIMING))
    }
    @Test fun ssrf_oobOnlyWhenCollaboratorSet() {
        assertFalse(ScanPayloads.ssrfProbes(null).any { it.detect == ScanPayloads.Detect.OOB })
        assertTrue(ScanPayloads.ssrfProbes("x.oast.pro").any { it.detect == ScanPayloads.Detect.OOB })
    }
    @Test fun all_combines() {
        val all = ScanPayloads.all("vz1", null)
        assertTrue(all.any { it.category == ScanPayloads.Category.XSS })
        assertTrue(all.any { it.category == ScanPayloads.Category.SQLI })
        assertTrue(all.any { it.category == ScanPayloads.Category.SSRF })
    }
}
