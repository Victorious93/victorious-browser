package com.victorious.browser.fuzz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherEngineTest {
    private fun resp(status: Int?, body: String = "", headers: String = "") =
        TemplateResponse(status, body, headers)

    @Test fun statusMatcher() {
        val m = TemplateMatcher(TemplateMatcher.Type.STATUS, values = listOf("200", "301"))
        assertTrue(MatcherEngine.matcherHits(m, resp(301)))
        assertFalse(MatcherEngine.matcherHits(m, resp(404)))
        assertFalse(MatcherEngine.matcherHits(m, resp(null)))
    }

    @Test fun wordMatcher_andOr() {
        val and = TemplateMatcher(TemplateMatcher.Type.WORD, TemplateMatcher.Part.BODY,
            FuzzTemplate.Condition.AND, listOf("foo", "bar"))
        assertTrue(MatcherEngine.matcherHits(and, resp(200, "x foo y bar z")))
        assertFalse(MatcherEngine.matcherHits(and, resp(200, "only foo")))
        val or = and.copy(condition = FuzzTemplate.Condition.OR)
        assertTrue(MatcherEngine.matcherHits(or, resp(200, "only foo")))
        assertFalse(MatcherEngine.matcherHits(or, resp(200, "nothing")))
    }

    @Test fun wordMatcher_headerPart() {
        val m = TemplateMatcher(TemplateMatcher.Type.WORD, TemplateMatcher.Part.HEADER,
            values = listOf("Server: nginx"))
        assertTrue(MatcherEngine.matcherHits(m, resp(200, "body", "Server: nginx\nX: y")))
        assertFalse(MatcherEngine.matcherHits(m, resp(200, "Server: nginx", "")))
    }

    @Test fun regexMatcher_caseInsensitive() {
        val m = TemplateMatcher(TemplateMatcher.Type.REGEX, TemplateMatcher.Part.BODY,
            values = listOf("db_password=\\S+"))
        assertTrue(MatcherEngine.matcherHits(m, resp(200, "DB_PASSWORD=secret123")))
    }

    @Test fun regexMatcher_invalidPatternFalse() {
        val m = TemplateMatcher(TemplateMatcher.Type.REGEX, values = listOf("(unclosed"))
        assertFalse(MatcherEngine.matcherHits(m, resp(200, "whatever")))
    }

    @Test fun sizeMatcher() {
        val m = TemplateMatcher(TemplateMatcher.Type.SIZE, values = listOf("5"))
        assertTrue(MatcherEngine.matcherHits(m, resp(200, "12345")))
        assertFalse(MatcherEngine.matcherHits(m, resp(200, "123")))
    }

    @Test fun evaluate_andCondition() {
        val tpl = FuzzTemplate(
            id = "t", name = "T", severity = FuzzTemplate.Severity.HIGH,
            requests = listOf(TemplateRequest()),
            matchers = listOf(
                TemplateMatcher(TemplateMatcher.Type.STATUS, values = listOf("200")),
                TemplateMatcher(TemplateMatcher.Type.WORD, values = listOf("secret")),
            ),
            matchersCondition = FuzzTemplate.Condition.AND,
        )
        assertNotNull(MatcherEngine.evaluate(tpl, resp(200, "a secret here"), "http://h/x"))
        assertNull(MatcherEngine.evaluate(tpl, resp(200, "nothing"), "http://h/x"))
        assertNull(MatcherEngine.evaluate(tpl, resp(404, "a secret here"), "http://h/x"))
    }

    @Test fun evaluate_orCondition_reportsHits() {
        val tpl = FuzzTemplate(
            id = "t", name = "T", severity = FuzzTemplate.Severity.LOW,
            requests = listOf(TemplateRequest()),
            matchers = listOf(
                TemplateMatcher(TemplateMatcher.Type.STATUS, values = listOf("500")),
                TemplateMatcher(TemplateMatcher.Type.WORD, values = listOf("secret")),
            ),
            matchersCondition = FuzzTemplate.Condition.OR,
        )
        val f = MatcherEngine.evaluate(tpl, resp(200, "a secret here"), "http://h/x")
        assertNotNull(f)
        assertEquals(1, f!!.matchedBy.size)
        assertTrue(f.matchedBy[0].contains("word"))
    }

    @Test fun labelFormat() {
        val m = TemplateMatcher(TemplateMatcher.Type.WORD, TemplateMatcher.Part.BODY, values = listOf("a", "b"))
        assertEquals("word(body): a, b", MatcherEngine.label(m))
    }
}
