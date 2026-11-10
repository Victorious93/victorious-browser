package com.victorious.browser.osint.research

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ResearchCaseTest {
    private val utc = ZoneId.of("UTC")
    private val case = ResearchCase("c1", "Acme *recon*", 0L)
        .withTarget("acme.com").withTarget("ACME.com")
        .withEntry(ResearchCase.Entry("e2", 2_000, "dns", "acme.com", "DNS", "A 1.2.3.4\n```evil```", listOf("https://crt.sh/?q=acme.com")))
        .withEntry(ResearchCase.Entry("e1", 1_000, "phone", "+16502530000", "Phone", "valid"))

    @Test fun targetsDedupeAndEntriesAddTargets() {
        assertEquals(listOf("acme.com", "+16502530000"), case.targets)
        assertEquals(1, case.withoutEntry("e2").entries.size)
    }

    @Test fun jsonRoundTrip() {
        val json = ResearchCaseCodec.toJson(case)
        assertEquals(ResearchCase.FORMAT, json.getString("format"))
        assertEquals(case, ResearchCaseCodec.fromJson(JSONObject(json.toString())))
        val list = ResearchCaseCodec.listFromJson(ResearchCaseCodec.listToJson(listOf(case, case.copy(id = "c2"))))
        assertEquals(2, list.size)
        assertTrue(ResearchCaseCodec.listFromJson("not json").isEmpty())
    }

    @Test fun markdownIsChronologicalAndSafe() {
        val md = ResearchCaseCodec.toMarkdown(case, utc)
        assertTrue(md.startsWith("# Acme \\*recon\\*\n"))
        assertTrue(md.indexOf("1970-01-01 00:00:01 UTC — Phone") < md.indexOf("1970-01-01 00:00:02 UTC — DNS"))
        assertTrue(md.contains("- <https://crt.sh/?q=acme.com>"))
        assertFalse(md.contains("```evil```"))
        assertTrue(md.contains("authorized security research"))
        assertEquals(4, Regex("^```", RegexOption.MULTILINE).findAll(md).count())
    }
}
