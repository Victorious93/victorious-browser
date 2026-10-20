package com.victorious.browser.fuzz

import com.victorious.browser.fuzz.MiniYaml.asMapping
import com.victorious.browser.fuzz.MiniYaml.asScalar
import com.victorious.browser.fuzz.MiniYaml.asSequence
import com.victorious.browser.fuzz.MiniYaml.node
import com.victorious.browser.fuzz.MiniYaml.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MiniYamlTest {
    @Test fun simpleMapping() {
        val n = MiniYaml.parse("id: foo\nname: bar")
        val m = n.asMapping()!!
        assertEquals("foo", m.str("id"))
        assertEquals("bar", m.str("name"))
    }

    @Test fun nestedMapping() {
        val n = MiniYaml.parse("info:\n  name: Test\n  severity: high")
        val info = n.asMapping()!!.node("info")!!.asMapping()!!
        assertEquals("Test", info.str("name"))
        assertEquals("high", info.str("severity"))
    }

    @Test fun scalarSequence() {
        val n = MiniYaml.parse("values:\n  - a\n  - b\n  - c")
        val seq = n.asMapping()!!.node("values")!!.asSequence()!!
        assertEquals(listOf("a", "b", "c"), seq.items.map { it.asScalar() })
    }

    @Test fun sequenceOfMappings() {
        val yaml = """
            requests:
              - method: GET
                path: /a
              - method: POST
                path: /b
        """.trimIndent()
        val reqs = MiniYaml.parse(yaml).asMapping()!!.node("requests")!!.asSequence()!!
        assertEquals(2, reqs.items.size)
        assertEquals("GET", reqs.items[0].asMapping()!!.str("method"))
        assertEquals("/b", reqs.items[1].asMapping()!!.str("path"))
    }

    @Test fun commentsAndBlanksIgnored() {
        val yaml = "# top comment\nid: x\n\n  # indented comment\nname: y  # trailing\n"
        val m = MiniYaml.parse(yaml).asMapping()!!
        assertEquals("x", m.str("id"))
        assertEquals("y", m.str("name"))
    }

    @Test fun quotedStringWithHash() {
        val m = MiniYaml.parse("a: \"has # hash\"").asMapping()!!
        assertEquals("has # hash", m.str("a"))
    }

    @Test fun seqAtSameIndentAsKey() {
        val yaml = "values:\n- a\n- b"
        val seq = MiniYaml.parse(yaml).asMapping()!!.node("values")!!.asSequence()!!
        assertEquals(listOf("a", "b"), seq.items.map { it.asScalar() })
    }

    @Test fun missingKeyNull() {
        assertNull(MiniYaml.parse("a: 1").asMapping()!!.str("b"))
        assertNotNull(MiniYaml.parse("a: 1").asMapping()!!.str("a"))
    }
}
