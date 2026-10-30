package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class StorageInspectorTest {

    private val token: String = run {
        fun enc(s: String) =
            Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        "${enc("""{"alg":"none"}""")}.${enc("""{"sub":"a"}""")}.sig"
    }

    @Test
    fun parseDumpFromObjectJson() {
        val raw = """
            {"origin":"https://app.example","localStorage":[{"k":"theme","v":"dark"},{"k":"jwt","v":"$token"}],"sessionStorage":[]}
        """.trimIndent()
        val dump = StorageInspector.parseDump(raw)
        assertEquals("https://app.example", dump.origin)
        assertEquals(2, dump.local.size)
        assertEquals("theme", dump.local[0].key)
        assertNull(dump.local[0].jwt)
        assertNotNull(dump.local[1].jwt)
        assertEquals("a", dump.local[1].jwt!!.claims["sub"])
        assertTrue(dump.session.isEmpty())
        assertNull(dump.error)
    }

    @Test
    fun unwrapsDoubleEncodedCallback() {
        val inner = """{"origin":"https://x.test","localStorage":[{"k":"a","v":"1"}],"sessionStorage":[]}"""
        val quoted = org.json.JSONObject.quote(inner)
        val dump = StorageInspector.parseDump(quoted)
        assertEquals("https://x.test", dump.origin)
        assertEquals("1", dump.local.single().value)
    }

    @Test
    fun parseDumpNullAndBad() {
        assertEquals("No response from page", StorageInspector.parseDump(null).error)
        assertTrue(StorageInspector.parseDump("not-json").error != null)
    }

    @Test
    fun setScriptQuotesKeyAndValue() {
        val js = StorageInspector.setScript(StorageInspector.Store.LOCAL, "a\"b", "x\ny")
        assertTrue(js.contains("localStorage.setItem"))
        assertTrue(js.contains("\\n") || js.contains("\n"))
        assertTrue(StorageInspector.removeScript(StorageInspector.Store.SESSION, "k").contains("sessionStorage.removeItem"))
        assertTrue(StorageInspector.clearScript(StorageInspector.Store.LOCAL).contains("localStorage.clear()"))
    }

    @Test
    fun parseMutation() {
        assertEquals(true to null, StorageInspector.parseMutation("""{"ok":true}"""))
        val fail = StorageInspector.parseMutation("""{"ok":false,"error":"Quota"}""")
        assertFalse(fail.first)
        assertEquals("Quota", fail.second)
        assertFalse(StorageInspector.parseMutation(null).first)
    }

    @Test
    fun exportJson() {
        val dump = StorageInspector.Dump("https://x.test", emptyList(), emptyList())
        val json = StorageInspector.exportJson(dump, 7)
        assertTrue(json.contains("victorious-browser.web-storage"))
        assertTrue(json.contains("https://x.test"))
    }
}
