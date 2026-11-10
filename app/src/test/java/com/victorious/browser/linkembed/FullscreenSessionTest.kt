package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullscreenSessionTest {
    @Test
    fun enterThenExitNotifiesOnce() {
        val hidden = mutableListOf<String>()
        val s = FullscreenSession<String, String> { hidden += it }
        assertTrue(s.enter("v1", "cb1"))
        assertTrue(s.isActive)
        assertEquals("v1", s.view)
        s.exit()
        assertFalse(s.isActive)
        assertNull(s.view)
        assertEquals(listOf("cb1"), hidden)
        s.exit() // no-op
        assertEquals(listOf("cb1"), hidden)
    }

    @Test
    fun secondEnterRefusedAndCallbackToldToHide() {
        val hidden = mutableListOf<String>()
        val s = FullscreenSession<String, String> { hidden += it }
        assertTrue(s.enter("v1", "cb1"))
        assertFalse(s.enter("v2", "cb2"))
        assertEquals("v1", s.view)
        assertEquals(listOf("cb2"), hidden) // only the refused callback
    }

    @Test
    fun pageHideClearsWithoutCallback() {
        val hidden = mutableListOf<String>()
        val s = FullscreenSession<String, String> { hidden += it }
        assertTrue(s.enter("v1", "cb1"))
        s.onHiddenByPage()
        assertFalse(s.isActive)
        assertTrue(hidden.isEmpty())
    }
}
