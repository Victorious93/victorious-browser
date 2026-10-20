package com.victorious.browser.fuzz

import com.victorious.browser.fuzz.MiniYaml.asMapping
import com.victorious.browser.fuzz.MiniYaml.asScalar
import com.victorious.browser.fuzz.MiniYaml.asSequence
import com.victorious.browser.fuzz.MiniYaml.node
import com.victorious.browser.fuzz.MiniYaml.str

/**
 * Maps a parsed [MiniYaml] node tree into a [FuzzTemplate]. Pure logic, fully
 * unit-tested in [TemplateParserTest]. Rejects templates missing required fields
 * with a clear message so the user knows what to fix.
 */
object TemplateParser {

    class InvalidTemplateException(message: String) : Exception(message)

    fun parse(yaml: String): FuzzTemplate {
        val root = try {
            MiniYaml.parse(yaml)
        } catch (e: MiniYaml.ParseException) {
            throw InvalidTemplateException("YAML parse error: ${e.message}")
        }
        val map = root.asMapping()
            ?: throw InvalidTemplateException("template root must be a mapping")

        val id = map.str("id")?.takeIf { it.isNotBlank() }
            ?: throw InvalidTemplateException("template missing required 'id'")

        val info = map.node("info")?.asMapping()
        val name = info?.str("name")?.takeIf { it.isNotBlank() } ?: id
        val severity = FuzzTemplate.Severity.parse(info?.str("severity"))

        val requests = parseRequests(map.node("requests") ?: map.node("http"))
        if (requests.isEmpty()) {
            throw InvalidTemplateException("template '$id' has no requests")
        }

        // Matchers may sit at the template level or under the first request block.
        val matcherNode = map.node("matchers")
            ?: (map.node("requests")?.asSequence()?.items?.firstOrNull()?.asMapping()?.node("matchers"))
        val matchers = parseMatchers(matcherNode)
        if (matchers.isEmpty()) {
            throw InvalidTemplateException("template '$id' has no matchers")
        }

        val matchersCondition = FuzzTemplate.Condition.parse(map.str("matchers-condition"))

        return FuzzTemplate(
            id = id,
            name = name,
            severity = severity,
            requests = requests,
            matchers = matchers,
            matchersCondition = matchersCondition,
        )
    }

    private fun parseRequests(node: MiniYaml.Node?): List<TemplateRequest> {
        val seq = node?.asSequence() ?: return emptyList()
        return seq.items.mapNotNull { item ->
            val m = item.asMapping() ?: return@mapNotNull null
            val method = (m.str("method") ?: "GET").uppercase()
            // Path can be a single scalar or a "path:" list; take the first path.
            val path = m.str("path")
                ?: m.node("path")?.asSequence()?.items?.firstOrNull()?.asScalar()
                ?: "/"
            val headers = m.node("headers")?.asMapping()?.entries
                ?.mapNotNull { (k, v) -> (v as? MiniYaml.Scalar)?.let { k to it.value } }
                ?.toMap()
                .orEmpty()
            val body = m.str("body")
            TemplateRequest(method = method, path = path, headers = headers, body = body)
        }
    }

    private fun parseMatchers(node: MiniYaml.Node?): List<TemplateMatcher> {
        val seq = node?.asSequence() ?: return emptyList()
        return seq.items.mapNotNull { item ->
            val m = item.asMapping() ?: return@mapNotNull null
            val type = TemplateMatcher.Type.parse(m.str("type")) ?: return@mapNotNull null
            val part = TemplateMatcher.Part.parse(m.str("part"))
            val condition = FuzzTemplate.Condition.parse(m.str("condition"))
            val values = parseValues(m.node("values") ?: m.node("words") ?: m.node("status"))
            if (values.isEmpty()) return@mapNotNull null
            TemplateMatcher(type = type, part = part, condition = condition, values = values)
        }
    }

    private fun parseValues(node: MiniYaml.Node?): List<String> {
        return when (node) {
            is MiniYaml.Sequence -> node.items.mapNotNull { it.asScalar() }
            is MiniYaml.Scalar -> if (node.value.isBlank()) emptyList() else listOf(node.value)
            else -> emptyList()
        }
    }
}
