package com.victorious.browser.fuzz

/**
 * Data model for the lightweight, Nuclei-style template engine.
 *
 * IMPORTANT (honesty): this is a *compatible subset* engine, not Nuclei. It
 * understands only the fields modeled below (id, info.name/severity,
 * requests[method/path/headers/body], matchers[type/part/condition/values]).
 * It does NOT claim compatibility with real Nuclei templates beyond this subset
 * — DSL expressions, extractors, workflows, payloads/attack modes, variables,
 * and the full matcher grammar are intentionally unsupported.
 */
data class FuzzTemplate(
    val id: String,
    val name: String,
    val severity: Severity,
    val requests: List<TemplateRequest>,
    val matchers: List<TemplateMatcher>,
    /** How matchers combine at the template level. Default AND (all must hit). */
    val matchersCondition: Condition = Condition.AND,
) {
    enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL, UNKNOWN;
        companion object {
            fun parse(s: String?): Severity = when (s?.trim()?.lowercase()) {
                "info" -> INFO
                "low" -> LOW
                "medium" -> MEDIUM
                "high" -> HIGH
                "critical" -> CRITICAL
                else -> UNKNOWN
            }
        }
    }

    enum class Condition { AND, OR;
        companion object {
            fun parse(s: String?): Condition =
                if (s?.trim()?.lowercase() == "or") OR else AND
        }
    }
}

data class TemplateRequest(
    val method: String = "GET",
    val path: String = "/",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class TemplateMatcher(
    val type: Type,
    val part: Part = Part.BODY,
    val condition: FuzzTemplate.Condition = FuzzTemplate.Condition.OR,
    val values: List<String>,
    /** For regex matchers, case-insensitive by default. */
    val caseInsensitive: Boolean = true,
) {
    enum class Type { STATUS, WORD, REGEX, SIZE;
        companion object {
            fun parse(s: String?): Type? = when (s?.trim()?.lowercase()) {
                "status" -> STATUS
                "word" -> WORD
                "regex" -> REGEX
                "size" -> SIZE
                else -> null
            }
        }
    }

    enum class Part { BODY, HEADER, STATUS;
        companion object {
            fun parse(s: String?): Part = when (s?.trim()?.lowercase()) {
                "header" -> HEADER
                "status" -> STATUS
                else -> BODY
            }
        }
    }
}

/** Normalized response fed to the matcher engine. */
data class TemplateResponse(
    val status: Int?,
    val body: String,
    /** Raw header block, one "Name: value" per line, for word/regex header matchers. */
    val headersText: String,
)

/** A matcher-engine finding. */
data class TemplateFinding(
    val templateId: String,
    val name: String,
    val severity: FuzzTemplate.Severity,
    val url: String,
    /** Which matchers fired, e.g. "word(body): [core]". */
    val matchedBy: List<String>,
)
