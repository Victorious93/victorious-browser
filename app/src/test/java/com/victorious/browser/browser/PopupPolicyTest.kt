package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PopupPolicyTest {

    @Test
    fun userGestureAllows() {
        assertEquals(
            PopupPolicy.Decision.Allow,
            PopupPolicy.decide(userGesture = true, siteSetting = PermissionDecision.ASK, allowedOnce = false, host = "a.com"),
        )
    }

    @Test
    fun noGestureBlocksUnlessAllowed() {
        val blocked = PopupPolicy.decide(false, PermissionDecision.ASK, false, "a.com")
        assertTrue(blocked is PopupPolicy.Decision.Block)
        assertEquals("a.com", (blocked as PopupPolicy.Decision.Block).host)

        assertEquals(PopupPolicy.Decision.Allow, PopupPolicy.decide(false, PermissionDecision.ALLOW, false, "a.com"))
        assertEquals(PopupPolicy.Decision.Allow, PopupPolicy.decide(false, PermissionDecision.ASK, true, "a.com"))
    }

    @Test
    fun explicitBlockBeatsGesture() {
        val d = PopupPolicy.decide(userGesture = true, siteSetting = PermissionDecision.BLOCK, allowedOnce = true, host = "a.com")
        assertTrue(d is PopupPolicy.Decision.Block)
    }
}

class FileChooserLogicTest {
    @Test
    fun mimeCollapse() {
        assertEquals("*/*", FileChooserLogic.mimeFor(null))
        assertEquals("*/*", FileChooserLogic.mimeFor(arrayOf()))
        assertEquals("image/png", FileChooserLogic.mimeFor(arrayOf("image/png")))
        assertEquals("image/*", FileChooserLogic.mimeFor(arrayOf("image/png", "image/jpeg")))
        assertEquals("*/*", FileChooserLogic.mimeFor(arrayOf("image/png", "application/pdf")))
        assertEquals("*/*", FileChooserLogic.mimeFor(arrayOf(".pdf"))) // extension only
    }

    @Test
    fun interpretModes() {
        val multi = FileChooserLogic.interpret(arrayOf("image/*"), FileChooserLogic.MODE_OPEN_MULTIPLE, captureEnabled = true)
        assertTrue(multi.allowMultiple)
        assertEquals("image/*", multi.mimeType)
        assertTrue(!multi.preferCapture) // capture suppressed for multi

        val singleCapture = FileChooserLogic.interpret(arrayOf("image/jpeg"), 0, captureEnabled = true)
        assertTrue(singleCapture.preferCapture)
        assertTrue(!singleCapture.allowMultiple)
    }
}

class RenderGoneLogicTest {
    @Test
    fun alwaysHandled() {
        assertTrue(RenderGoneLogic.HANDLED)
        assertTrue(RenderGoneLogic.message(true).contains("crashed"))
        assertTrue(RenderGoneLogic.message(false).contains("memory"))
    }
}
