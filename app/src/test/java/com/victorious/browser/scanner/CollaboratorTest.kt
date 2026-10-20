package com.victorious.browser.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollaboratorTest {
    @Test fun unconfigured_isInert() {
        val c = Collaborator.Config()
        assertFalse(Collaborator.isConfigured(c))
        assertNull(Collaborator.payloadHost(c, "abc"))
        assertNull(Collaborator.payloadUrl(c, "abc"))
    }
    @Test fun configured_generatesHost() {
        val c = Collaborator.Config("oast.pro", "https://oast.pro/poll")
        assertTrue(Collaborator.isConfigured(c))
        assertEquals("abc123.oast.pro", Collaborator.payloadHost(c, "ABC123"))
        assertEquals("http://abc123.oast.pro/", Collaborator.payloadUrl(c, "ABC123"))
    }
    @Test fun payloadHost_sanitizesToken() {
        val c = Collaborator.Config("oast.pro", "https://oast.pro/poll")
        assertEquals("probe.oast.pro", Collaborator.payloadHost(c, "!!!"))
    }
    @Test fun parse_jsonArray() {
        val json = """[{"protocol":"dns","remote-address":"1.2.3.4","timestamp":"t1"}]"""
        val list = Collaborator.parseInteractions(json)
        assertEquals(1, list.size)
        assertEquals("dns", list[0].type)
        assertEquals("1.2.3.4", list[0].sourceHost)
    }
    @Test fun parse_jsonObjectWithData() {
        val json = """{"data":[{"type":"http","source":"9.9.9.9","time":"t2"}]}"""
        val list = Collaborator.parseInteractions(json)
        assertEquals(1, list.size)
        assertEquals("http", list[0].type)
        assertEquals("9.9.9.9", list[0].sourceHost)
    }
    @Test fun parse_garbage_empty() {
        assertTrue(Collaborator.parseInteractions("not json").isEmpty())
        assertTrue(Collaborator.parseInteractions("").isEmpty())
    }
}
