package com.victorious.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptMatcherTest {
    @Test
    fun matchesExactHost() {
        assertTrue(UserScriptMatcher.matches("example.com", "example.com"))
    }

    @Test
    fun matchesSubdomainOfBarePattern() {
        assertTrue(UserScriptMatcher.matches("www.example.com", "example.com"))
    }

    @Test
    fun matchesWildcardPrefixPattern() {
        assertTrue(UserScriptMatcher.matches("www.example.com", "*.example.com"))
        assertTrue(UserScriptMatcher.matches("example.com", "*.example.com"))
    }

    @Test
    fun rejectsUnrelatedOrSuffixOnlyHosts() {
        assertFalse(UserScriptMatcher.matches("notexample.com", "example.com"))
        assertFalse(UserScriptMatcher.matches("example.com.evil.com", "example.com"))
    }

    @Test
    fun rejectsBlankInput() {
        assertFalse(UserScriptMatcher.matches(null, "example.com"))
        assertFalse(UserScriptMatcher.matches("example.com", ""))
    }

    @Test
    fun bareHostStripsLeadingWildcard() {
        assertEquals("example.com", UserScriptMatcher.bareHost("*.example.com"))
        assertEquals("example.com", UserScriptMatcher.bareHost("example.com"))
    }
}
