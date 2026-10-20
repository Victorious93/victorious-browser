package com.victorious.browser.fuzz

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateRunnerTest {
    @Test fun joinUrl_noDoubleSlash() {
        assertEquals("http://h/a", TemplateRunner.joinUrl("http://h/", "/a"))
        assertEquals("http://h/a", TemplateRunner.joinUrl("http://h", "a"))
        assertEquals("http://h/.git/config", TemplateRunner.joinUrl("http://h/", "/.git/config"))
    }
}
