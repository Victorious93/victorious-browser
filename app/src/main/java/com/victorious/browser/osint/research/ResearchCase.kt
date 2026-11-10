package com.victorious.browser.osint.research

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** A research case: targets plus timestamped results and notes. Pure model + export. */
data class ResearchCase(
    val id: String,
    val name: String,
    val createdAt: Long,
    val targets: List<String> = emptyList(),
    val entries: List<Entry> = emptyList(),
) {
    data class Entry(
        val id: String,
        val timestamp: Long,
        /** dork / phone / email / username / domain / ip / image / note */
        val kind: String,
        val target: String,
        val title: String,
        val body: String,
        val urls: List<String> = emptyList(),
    )

    fun withTarget(t: String): ResearchCase {
        val v = t.trim()
        return if (v.isEmpty() || targets.any { it.equals(v, true) }) this else copy(targets = targets + v)
    }

    fun withEntry(e: Entry): ResearchCase = withTarget(e.target).copy(entries = entries + e)
    fun withoutEntry(id: String): ResearchCase = copy(entries = entries.filterNot { it.id == id })

    companion object {
        const val FORMAT = "victorious.research.case/v1"
    }
}

object ResearchCaseCodec {
    fun toJson(c: ResearchCase): JSONObject = JSONObject()
        .put("format", ResearchCase.FORMAT)
        .put("id", c.id)
        .put("name", c.name)
        .put("createdAt", c.createdAt)
        .put("targets", JSONArray(c.targets))
        .put("entries", JSONArray().apply {
            c.entries.forEach { e ->
                put(
                    JSONObject()
                        .put("id", e.id).put("timestamp", e.timestamp).put("kind", e.kind)
                        .put("target", e.target).put("title", e.title).put("body", e.body)
                        .put("urls", JSONArray(e.urls)),
                )
            }
        })

    fun fromJson(o: JSONObject): ResearchCase {
        val ents = o.optJSONArray("entries") ?: JSONArray()
        return ResearchCase(
            id = o.getString("id"),
            name = o.optString("name", "Case"),
            createdAt = o.optLong("createdAt"),
            targets = o.optJSONArray("targets").toList(),
            entries = (0 until ents.length()).mapNotNull { i ->
                val e = ents.optJSONObject(i) ?: return@mapNotNull null
                ResearchCase.Entry(
                    id = e.optString("id"), timestamp = e.optLong("timestamp"), kind = e.optString("kind"),
                    target = e.optString("target"), title = e.optString("title"), body = e.optString("body"),
                    urls = e.optJSONArray("urls").toList(),
                )
            },
        )
    }

    fun listToJson(cases: List<ResearchCase>): String = JSONArray().apply { cases.forEach { put(toJson(it)) } }.toString()
    fun listFromJson(s: String): List<ResearchCase> = runCatching {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { runCatching { fromJson(it) }.getOrNull() } }
    }.getOrDefault(emptyList())

    private fun JSONArray?.toList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }

    fun isoTime(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(zone).format(Instant.ofEpochMilli(ms))

    /** Markdown report. Code fences in bodies are neutralized so the report can't break out. */
    fun toMarkdown(c: ResearchCase, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine("# ${mdEscape(c.name)}")
        appendLine()
        appendLine("_Created ${isoTime(c.createdAt, zone)} · ${c.entries.size} entries · Victorious Browser Deep Research_")
        appendLine()
        appendLine("> For authorized security research or your own data only. Results come from public sources and may be stale or wrong.")
        appendLine()
        appendLine("## Targets")
        if (c.targets.isEmpty()) appendLine("- (none)") else c.targets.forEach { appendLine("- `${it.replace("`", "'")}`") }
        appendLine()
        appendLine("## Timeline")
        c.entries.sortedBy { it.timestamp }.forEach { e ->
            appendLine()
            appendLine("### ${isoTime(e.timestamp, zone)} — ${mdEscape(e.title)}")
            appendLine("*${e.kind}* · target `${e.target.replace("`", "'")}`")
            if (e.body.isNotBlank()) {
                appendLine()
                appendLine("```")
                appendLine(e.body.replace("```", "'''"))
                appendLine("```")
            }
            e.urls.forEach { appendLine("- <${it.replace(">", "%3E")}>") }
        }
    }.trimEnd() + "\n"

    fun mdEscape(s: String): String = s.replace(Regex("([\\\\`*_\\[\\]#<>|])"), "\\\\$1").replace('\n', ' ')
}
