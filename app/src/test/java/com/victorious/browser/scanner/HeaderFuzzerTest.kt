package com.victorious.browser.scanner

import com.victorious.browser.inspect.CapturedHeader
import com.victorious.browser.inspect.EditableRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderFuzzerTest {
    private val req = EditableRequest(
        "GET", "https://example.com/",
        listOf(CapturedHeader("Host", "example.com"), CapturedHeader("Accept", "*/*")),
    )
    @Test fun apply_addsNewHeader() {
        val mut = HeaderFuzzer.Mutation("X-Forwarded-For", "127.0.0.1", "n")
        val out = HeaderFuzzer.apply(req, mut, "evil.example")
        assertEquals("127.0.0.1", out.headers.first { it.name == "X-Forwarded-For" }.value)
    }
    @Test fun apply_overridesExistingHostAndSubstitutesEvil() {
        val mut = HeaderFuzzer.Mutation("Host", "{evil}", "n")
        val out = HeaderFuzzer.apply(req, mut, "evil.example")
        val hosts = out.headers.filter { it.name.equals("Host", true) }
        assertEquals(1, hosts.size)
        assertEquals("evil.example", hosts[0].value)
    }
    @Test fun builtIn_nonEmptyAndHasXff() {
        assertTrue(HeaderFuzzer.BUILT_IN.any { it.name == "X-Forwarded-For" })
        assertTrue(HeaderFuzzer.BUILT_IN.any { it.name == "Host" })
    }
    @Test fun diff_flagsStatusChange() {
        val mut = HeaderFuzzer.BUILT_IN.first()
        val d = HeaderFuzzer.diff(mut, 200, "aaaa", 403, "aaaa")
        assertTrue(d.statusChanged)
        assertTrue(d.interesting)
    }
    @Test fun diff_flagsLengthDelta() {
        val mut = HeaderFuzzer.BUILT_IN.first()
        val d = HeaderFuzzer.diff(mut, 200, "a".repeat(10), 200, "a".repeat(100))
        assertFalse(d.statusChanged)
        assertTrue(d.interesting)
    }
    @Test fun diff_notInterestingWhenSame() {
        val mut = HeaderFuzzer.BUILT_IN.first()
        val d = HeaderFuzzer.diff(mut, 200, "abc", 200, "abc")
        assertFalse(d.interesting)
    }
}
