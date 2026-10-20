package com.victorious.browser.sandbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxNameTest {
    @Test
    fun acceptsOrdinaryNames() {
        assertTrue(SandboxName.isValid("work"))
        assertTrue(SandboxName.isValid("personal_2"))
        assertTrue(SandboxName.isValid("a-b-c"))
    }

    @Test
    fun rejectsReservedDefaultName() {
        assertFalse(SandboxName.isValid("Default"))
    }

    @Test
    fun rejectsBlankOrOverlongNames() {
        assertFalse(SandboxName.isValid(""))
        assertFalse(SandboxName.isValid("a".repeat(51)))
        assertTrue(SandboxName.isValid("a".repeat(50)))
    }

    @Test
    fun rejectsNamesWithDisallowedCharacters() {
        assertFalse(SandboxName.isValid("has space"))
        assertFalse(SandboxName.isValid("has/slash"))
        assertFalse(SandboxName.isValid("has.dot"))
        assertFalse(SandboxName.isValid("emoji😀"))
    }
}
