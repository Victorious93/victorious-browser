package com.victorious.browser.fuzz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateParserTest {
    private val valid = """
        id: git-config
        info:
          name: Exposed .git/config
          severity: medium
        requests:
          - method: GET
            path: /.git/config
            headers:
              X-Test: foo
        matchers:
          - type: status
            values:
              - 200
          - type: word
            part: body
            condition: and
            values:
              - "[core]"
              - repositoryformatversion
        matchers-condition: and
    """.trimIndent()

    @Test fun parsesFullTemplate() {
        val t = TemplateParser.parse(valid)
        assertEquals("git-config", t.id)
        assertEquals("Exposed .git/config", t.name)
        assertEquals(FuzzTemplate.Severity.MEDIUM, t.severity)
        assertEquals(1, t.requests.size)
        assertEquals("GET", t.requests[0].method)
        assertEquals("/.git/config", t.requests[0].path)
        assertEquals("foo", t.requests[0].headers["X-Test"])
        assertEquals(2, t.matchers.size)
        assertEquals(TemplateMatcher.Type.STATUS, t.matchers[0].type)
        assertEquals(FuzzTemplate.Condition.AND, t.matchers[1].condition)
        assertEquals(listOf("[core]", "repositoryformatversion"), t.matchers[1].values)
        assertEquals(FuzzTemplate.Condition.AND, t.matchersCondition)
    }

    @Test fun missingIdThrows() {
        val e = assertThrows(TemplateParser.InvalidTemplateException::class.java) {
            TemplateParser.parse("info:\n  name: x\nrequests:\n  - path: /a\nmatchers:\n  - type: status\n    values:\n      - 200")
        }
        assertTrue(e.message!!.contains("id"))
    }

    @Test fun noRequestsThrows() {
        assertThrows(TemplateParser.InvalidTemplateException::class.java) {
            TemplateParser.parse("id: x\nmatchers:\n  - type: status\n    values:\n      - 200")
        }
    }

    @Test fun noMatchersThrows() {
        assertThrows(TemplateParser.InvalidTemplateException::class.java) {
            TemplateParser.parse("id: x\nrequests:\n  - path: /a")
        }
    }

    @Test fun defaultsApplied() {
        val t = TemplateParser.parse("id: x\nrequests:\n  - path: /a\nmatchers:\n  - type: word\n    values:\n      - hi")
        assertEquals(FuzzTemplate.Severity.UNKNOWN, t.severity)
        assertEquals("x", t.name)
        assertEquals("GET", t.requests[0].method)
        assertEquals(TemplateMatcher.Part.BODY, t.matchers[0].part)
    }
}
