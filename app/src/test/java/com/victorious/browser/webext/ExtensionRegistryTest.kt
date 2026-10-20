package com.victorious.browser.webext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionRegistryTest {
    private fun src(name: String = "Demo", ns: String = "ns", grant: String = "", runAt: String = "document-idle", match: String = "*://*.example.com/*") = """
        // ==UserScript==
        // @name         $name
        // @namespace    $ns
        // @version      1.0
        // @match        $match
        // @run-at       $runAt
        ${if (grant.isNotBlank()) "// @grant        $grant\n        // @grant        GM_getValue\n        // @grant        GM_setValue\n        // @grant        GM_deleteValue\n        // @grant        GM_listValues" else ""}
        // ==/UserScript==
        console.log('x');
    """.trimIndent()

    @Test fun installAndReplaceSameNameNs() {
        val reg = ExtensionRegistry(clock = { 42L }, newId = { "id-1" })
        val a = reg.install(src()) as ExtensionRegistry.InstallResult.Installed
        assertEquals("id-1", a.extension.id)
        assertFalse(a.replaced)
        val b = reg.install(src().replace("1.0", "2.0")) as ExtensionRegistry.InstallResult.Installed
        assertTrue(b.replaced)
        assertEquals("id-1", b.extension.id)
        assertEquals(1, reg.extensions.size)
        assertEquals("2.0", reg.extensions[0].version)
    }

    @Test fun rejectOversized() {
        val big = "// ==UserScript==\n// @name X\n// ==/UserScript==\n" + "x".repeat(ExtensionRegistry.MAX_SOURCE_BYTES)
        val r = ExtensionRegistry().install(big)
        assertTrue(r is ExtensionRegistry.InstallResult.Rejected)
    }

    @Test fun enableDisableDelete() {
        val reg = ExtensionRegistry(newId = { "a" })
        reg.install(src())
        assertTrue(reg.setEnabled("a", false))
        assertFalse(reg.extensions[0].enabled)
        assertTrue(reg.delete("a"))
        assertTrue(reg.extensions.isEmpty())
    }

    @Test fun forPageRespectsRunAtMatchAndPause() {
        val reg = ExtensionRegistry(newId = { "a" })
        reg.install(src(runAt = "document-start"))
        assertEquals(1, reg.forPage("https://www.example.com/x", RunAt.DOCUMENT_START).size)
        assertEquals(0, reg.forPage("https://www.example.com/x", RunAt.DOCUMENT_IDLE).size)
        assertEquals(0, reg.forPage("https://other.com/x", RunAt.DOCUMENT_START).size)
        reg.pausedHosts = setOf("www.example.com")
        assertEquals(0, reg.forPage("https://www.example.com/x", RunAt.DOCUMENT_START).size)
    }

    @Test fun gmValuesRequireGrantAndCap() {
        val reg = ExtensionRegistry(newId = { "a" })
        reg.install(src(grant = "GM_log")) // also adds get/set via template when grant nonblank
        assertTrue(reg.setValue("a", "k", "\"v\""))
        assertEquals("\"v\"", reg.getValue("a", "k"))
        assertEquals(listOf("k"), reg.listValues("a"))
        assertTrue(reg.deleteValue("a", "k"))
        assertNull(reg.getValue("a", "k"))

        val no = ExtensionRegistry(newId = { "b" })
        no.install(src(name = "NoGrant")) // no grants
        assertFalse(no.setValue("b", "k", "v"))
        assertNull(no.getValue("b", "k"))
    }

    @Test fun codecRoundTrip() {
        val reg = ExtensionRegistry(clock = { 99L }, newId = { "id" })
        reg.install(src(grant = "GM_log"))
        reg.setValue("id", "k", "\"hello\"")
        val raw = ExtensionCodec.encode(reg.extensions, reg.snapshotValues())
        val decoded = ExtensionCodec.decode(raw)
        assertEquals(1, decoded.extensions.size)
        assertEquals("Demo", decoded.extensions[0].name)
        assertEquals("\"hello\"", decoded.values["id"]?.get("k"))
        assertEquals(emptyList<Extension>(), ExtensionCodec.decode(null).extensions)
        assertEquals(emptyList<Extension>(), ExtensionCodec.decode("{bad").extensions)
    }

    @Test fun wrapProducesInjectableJs() {
        val ext = Extension.fromMeta(
            "id", UserscriptMeta(name = "N", version = "1", matches = listOf("*://*/*"),
                grants = listOf("GM_getValue")), "console.log(1)", 0L,
        )
        val js = GmShim.wrap(ext)
        assertTrue(js.contains("__victoriousGM"))
        assertTrue(js.contains("GM_getValue"))
        assertTrue(js.contains("console.log(1)"))
    }
}
