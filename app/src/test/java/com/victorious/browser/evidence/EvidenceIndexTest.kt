package com.victorious.browser.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceIndexTest {

    private fun sampleCase() = EvidenceCase(
        id = "case-1",
        name = "Recon Alpha",
        createdAt = 1_700_000_000_000,
        items = listOf(
            EvidenceItem("i1", EvidenceType.SCREENSHOT, 1_700_000_001_000, "example.com", "home", fileRef = "shot-1.png"),
            EvidenceItem("i2", EvidenceType.REQUEST, 1_700_000_002_000, "example.com", "GET /", data = "req-data"),
            EvidenceItem("i3", EvidenceType.FINDING, 1_700_000_003_000, "example.com", "XSS", data = "detail"),
            EvidenceItem("i4", EvidenceType.NOTE, 1_700_000_004_000, "", "note", note = "remember this"),
        ),
    )

    @Test fun roundTrips() {
        val original = sampleCase()
        val parsed = EvidenceIndex.fromJson(EvidenceIndex.toJson(original))
        assertEquals(original, parsed)
    }

    @Test fun nullFieldsStayNull() {
        val parsed = EvidenceIndex.fromJson(EvidenceIndex.toJson(sampleCase()))
        val shot = parsed.items.first { it.id == "i1" }
        assertNull(shot.data)
        assertNull(shot.note)
        assertEquals("shot-1.png", shot.fileRef)
    }

    @Test fun unknownTypeDefaultsToNote() {
        val json = """{"id":"c","name":"n","createdAt":1,"items":[{"id":"x","type":"BOGUS","timestamp":2,"host":"h","title":"t"}]}"""
        val parsed = EvidenceIndex.fromJson(json)
        assertEquals(EvidenceType.NOTE, parsed.items[0].type)
    }

    @Test fun countByType() {
        val c = sampleCase()
        assertEquals(1, c.countByType(EvidenceType.SCREENSHOT))
        assertEquals(1, c.countByType(EvidenceType.FINDING))
    }
}
