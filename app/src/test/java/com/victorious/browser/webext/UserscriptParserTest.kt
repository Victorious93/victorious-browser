package com.victorious.browser.webext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserscriptParserTest {
    private val sample = """
        // ==UserScript==
        // @name         Demo Script
        // @namespace    com.example
        // @version      1.2.3
        // @description  A demo
        // @match        *://*.example.com/*
        // @match        https://foo.test/bar*
        // @include      /https?:\/\/evil\.test\/.*/i
        // @exclude      *://*.example.com/admin*
        // @run-at       document-start
        // @grant        GM_getValue
        // @grant        GM_setValue
        // @grant        GM_xmlhttpRequest
        // @require      https://cdn.example/lib.js
        // @icon         https://example.com/icon.png
        // ==/UserScript==
        console.log('hi');
    """.trimIndent()

    @Test fun parsesCoreFields() {
        val r = UserscriptParser.parse(sample) as ParseResult.Ok
        assertEquals("Demo Script", r.meta.name)
        assertEquals("com.example", r.meta.namespace)
        assertEquals("1.2.3", r.meta.version)
        assertEquals("A demo", r.meta.description)
        assertEquals(RunAt.DOCUMENT_START, r.meta.runAt)
        assertEquals(2, r.meta.matches.size)
        assertEquals(listOf("GM_getValue", "GM_setValue", "GM_xmlhttpRequest"), r.meta.grants)
        assertEquals("https://example.com/icon.png", r.meta.icon)
        assertTrue(r.warnings.any { it.contains("@require") })
        assertTrue(r.warnings.any { it.contains("GM_xmlhttpRequest") })
    }

    @Test fun rejectsMissingBlock() {
        val r = UserscriptParser.parse("console.log(1)")
        assertTrue(r is ParseResult.Error)
    }

    @Test fun rejectsMissingName() {
        val src = """
            // ==UserScript==
            // @version 1
            // ==/UserScript==
        """.trimIndent()
        assertTrue(UserscriptParser.parse(src) is ParseResult.Error)
    }

    @Test fun rejectsUnclosedBlock() {
        val src = """
            // ==UserScript==
            // @name X
        """.trimIndent()
        assertTrue(UserscriptParser.parse(src) is ParseResult.Error)
    }

    @Test fun defaultsRunAtToIdle() {
        val src = """
            // ==UserScript==
            // @name X
            // @match *://*/*
            // ==/UserScript==
        """.trimIndent()
        val r = UserscriptParser.parse(src) as ParseResult.Ok
        assertEquals(RunAt.DOCUMENT_IDLE, r.meta.runAt)
    }

    @Test fun dropsInvalidMatch() {
        val src = """
            // ==UserScript==
            // @name X
            // @match not-a-pattern
            // @match *://ok.example/*
            // ==/UserScript==
        """.trimIndent()
        val r = UserscriptParser.parse(src) as ParseResult.Ok
        assertEquals(listOf("*://ok.example/*"), r.meta.matches)
        assertTrue(r.warnings.any { it.contains("Invalid @match") })
    }
}
