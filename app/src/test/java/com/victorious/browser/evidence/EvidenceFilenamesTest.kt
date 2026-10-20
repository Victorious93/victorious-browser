package com.victorious.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceFilenamesTest {

    @Test fun sanitizeRemovesPathTraversal() {
        assertFalse(EvidenceFilenames.sanitize("../../etc/passwd").contains("/"))
        assertFalse(EvidenceFilenames.sanitize("..").contains(".."))
        assertEquals("item", EvidenceFilenames.sanitize("   "))
        assertEquals("item", EvidenceFilenames.sanitize(".."))
    }

    @Test fun sanitizeKeepsSafeChars() {
        assertEquals("my_case_1.0", EvidenceFilenames.sanitize("my case/1.0"))
    }

    @Test fun caseFolderNameHasTimestampPrefix() {
        assertEquals("123-Recon_A", EvidenceFilenames.caseFolderName(123, "Recon A"))
    }

    @Test fun screenshotAndZipNames() {
        assertTrue(EvidenceFilenames.screenshotName(5, "a.com").startsWith("shot-5-"))
        assertTrue(EvidenceFilenames.screenshotName(5, "a.com").endsWith(".png"))
        assertTrue(EvidenceFilenames.caseZipName("Case X", 9).endsWith(".zip"))
        assertTrue(EvidenceFilenames.caseZipName("Case X", 9).contains("Case_X"))
    }
}
