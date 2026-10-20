package com.victorious.browser.userscript

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScriptInjectorTest {
    @Test
    fun originRulesCoverBothSchemesAndSubdomainWildcard() {
        val rules = UserScriptInjector.originRulesFor("example.com")
        assertEquals(
            setOf("https://example.com", "https://*.example.com", "http://example.com", "http://*.example.com"),
            rules,
        )
    }

    @Test
    fun originRulesStripLeadingWildcardFromPattern() {
        assertEquals(UserScriptInjector.originRulesFor("example.com"), UserScriptInjector.originRulesFor("*.example.com"))
    }

    @Test
    fun blankPatternYieldsNoRules() {
        assertTrue(UserScriptInjector.originRulesFor("").isEmpty())
    }
}
