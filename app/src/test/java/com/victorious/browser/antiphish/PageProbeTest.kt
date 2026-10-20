package com.victorious.browser.antiphish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageProbeTest {

    @Test
    fun parse_snapshotWithoutValues() {
        val raw = """
            {"title":"Sign in","https":true,"hints":["AcmePay logo"],"forms":[
              {"action":"https://evil.xyz/c","actionHost":"evil.xyz","hasPassword":true,"hasUsername":true,"hasHiddenCred":false,"passwordCount":1}
            ]}
        """.trimIndent()
        // Simulate evaluateJavascript quoting
        val quoted = org.json.JSONObject.quote(raw)
        val snap = PageProbe.parse(quoted)
        assertNotNull(snap)
        assertEquals("Sign in", snap!!.title)
        assertTrue(snap.https)
        assertEquals(1, snap.forms.size)
        assertTrue(snap.forms[0].hasPasswordField)
        assertEquals("evil.xyz", snap.forms[0].actionHost)
        assertEquals(listOf("AcmePay logo"), snap.brandHints)
        // Ensure we never invent field values
        assertFalse(raw.contains("value"))
    }

    @Test
    fun unwrap_nullAndEmpty() {
        assertNull(PageProbe.unwrapJsString("null"))
        assertNull(PageProbe.unwrapJsString(""))
        assertEquals("{}", PageProbe.unwrapJsString("\"{}\""))
    }

    @Test
    fun parse_plainJson() {
        val snap = PageProbe.parse("""{"title":"t","https":false,"hints":[],"forms":[]}""")
        assertNotNull(snap)
        assertFalse(snap!!.https)
    }
}
