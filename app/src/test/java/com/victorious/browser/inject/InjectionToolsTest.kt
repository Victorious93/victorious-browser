package com.victorious.browser.inject

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectionToolsTest {
    @Test
    fun scriptInlinesHtmlAsAnEscapedStringLiteral() {
        val script = InjectionTools.buildHtmlInjectionScript("""<img src=x onerror="alert(1)">""")
        assertTrue(script.contains("insertAdjacentHTML"))
        assertTrue(script.contains("\\\"alert(1)\\\""))
    }

    @Test
    fun parsesSuccessOutcome() {
        val articleJson = """{"ok":true}"""
        val callbackValue = JSONObject().put("v", articleJson).toString().removePrefix("{\"v\":").removeSuffix("}")
        val outcome = InjectionTools.parseInjectionResult(callbackValue)
        assertTrue(outcome.ok)
        assertNull(outcome.error)
    }

    @Test
    fun parsesFailureOutcomeWithError() {
        val articleJson = """{"ok":false,"error":"TypeError: boom"}"""
        val callbackValue = JSONObject().put("v", articleJson).toString().removePrefix("{\"v\":").removeSuffix("}")
        val outcome = InjectionTools.parseInjectionResult(callbackValue)
        assertEquals(false, outcome.ok)
        assertEquals("TypeError: boom", outcome.error)
    }

    @Test
    fun treatsNullOrBlankCallbackAsFailure() {
        assertEquals(false, InjectionTools.parseInjectionResult(null).ok)
        assertEquals(false, InjectionTools.parseInjectionResult("").ok)
        assertEquals(false, InjectionTools.parseInjectionResult("null").ok)
    }

    @Test
    fun treatsMalformedCallbackAsFailure() {
        assertEquals(false, InjectionTools.parseInjectionResult("{not json").ok)
    }
}
