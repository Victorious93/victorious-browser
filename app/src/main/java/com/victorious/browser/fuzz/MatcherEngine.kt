package com.victorious.browser.fuzz

/**
 * Pure matcher-evaluation logic for the template engine. Heavily unit-tested in
 * [MatcherEngineTest]; no Android/network types. Given a [FuzzTemplate]'s
 * matchers and a [TemplateResponse], decides whether the template fires and
 * reports which matchers hit.
 */
object MatcherEngine {

    /** Evaluate one matcher against a response. Returns true if it fires. */
    fun matcherHits(m: TemplateMatcher, resp: TemplateResponse): Boolean {
        return when (m.type) {
            TemplateMatcher.Type.STATUS -> {
                val codes = m.values.mapNotNull { it.trim().toIntOrNull() }
                resp.status != null && resp.status in codes
            }
            TemplateMatcher.Type.SIZE -> {
                val sizes = m.values.mapNotNull { it.trim().toIntOrNull() }
                val actual = resp.body.toByteArray(Charsets.UTF_8).size
                combine(m.condition, sizes.map { it == actual })
            }
            TemplateMatcher.Type.WORD -> {
                val hay = partText(m.part, resp)
                combine(m.condition, m.values.map { hay.contains(it) })
            }
            TemplateMatcher.Type.REGEX -> {
                val hay = partText(m.part, resp)
                val opts = if (m.caseInsensitive) setOf(RegexOption.IGNORE_CASE) else emptySet()
                combine(
                    m.condition,
                    m.values.map { pat ->
                        try {
                            Regex(pat, opts).containsMatchIn(hay)
                        } catch (_: Exception) {
                            false
                        }
                    },
                )
            }
        }
    }

    private fun partText(part: TemplateMatcher.Part, resp: TemplateResponse): String =
        when (part) {
            TemplateMatcher.Part.BODY -> resp.body
            TemplateMatcher.Part.HEADER -> resp.headersText
            TemplateMatcher.Part.STATUS -> resp.status?.toString() ?: ""
        }

    private fun combine(cond: FuzzTemplate.Condition, results: List<Boolean>): Boolean {
        if (results.isEmpty()) return false
        return when (cond) {
            FuzzTemplate.Condition.AND -> results.all { it }
            FuzzTemplate.Condition.OR -> results.any { it }
        }
    }

    /** Human-readable label of a matcher, for the "matched by" evidence. */
    fun label(m: TemplateMatcher): String {
        val head = m.type.name.lowercase()
        val part = if (m.type == TemplateMatcher.Type.WORD || m.type == TemplateMatcher.Type.REGEX) {
            "(${m.part.name.lowercase()})"
        } else {
            ""
        }
        return "$head$part: ${m.values.joinToString(", ")}"
    }

    /**
     * Evaluate every matcher of [template] against [resp]. Returns a finding when
     * the template fires per its [FuzzTemplate.matchersCondition], else null.
     */
    fun evaluate(template: FuzzTemplate, resp: TemplateResponse, url: String): TemplateFinding? {
        val hits = template.matchers.map { it to matcherHits(it, resp) }
        val fired = when (template.matchersCondition) {
            FuzzTemplate.Condition.AND -> hits.all { it.second }
            FuzzTemplate.Condition.OR -> hits.any { it.second }
        }
        if (!fired) return null
        return TemplateFinding(
            templateId = template.id,
            name = template.name,
            severity = template.severity,
            url = url,
            matchedBy = hits.filter { it.second }.map { label(it.first) },
        )
    }
}
