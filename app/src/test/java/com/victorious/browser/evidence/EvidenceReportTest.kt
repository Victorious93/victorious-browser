package com.victorious.browser.evidence

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceReportTest {

    private val case = EvidenceCase(
        id = "c1",
        name = "Recon <Alpha>",
        createdAt = 1_700_000_000_000,
        items = listOf(
            EvidenceItem("i1", EvidenceType.SCREENSHOT, 1_700_000_001_000, "example.com", "home", fileRef = "shot.png"),
            EvidenceItem("i2", EvidenceType.FINDING, 1_700_000_002_000, "example.com", "Bug <b>", data = "x < y & z"),
        ),
    )

    @Test fun markdownSummarizesItems() {
        val md = EvidenceReport.markdown(case)
        assertTrue(md.contains("# Evidence Case: Recon <Alpha>"))
        assertTrue(md.contains("[SCREENSHOT] home"))
        assertTrue(md.contains("![home](shot.png)"))
        assertTrue(md.contains("Items: 2"))
    }

    @Test fun htmlEscapesUnsafeChars() {
        val html = EvidenceReport.html(case)
        assertTrue(html.contains("&lt;Alpha&gt;"))
        assertTrue(html.contains("x &lt; y &amp; z"))
        // raw unescaped angle in title must not leak
        assertFalse(html.contains("Bug <b>"))
    }
}
