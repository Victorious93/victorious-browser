package com.victorious.browser.help

/**
 * A tiny, dependency-free Markdown model + parser for the in-app manual.
 *
 * It intentionally supports only the small subset the bundled `MANUAL.md` uses:
 * ATX headings (`#`..`####`), unordered list items (`-`/`*`), fenced code
 * blocks (``` ```), pipe tables (rendered as a monospace block), and
 * paragraphs. No external markdown dependency is pulled in.
 *
 * All logic here is pure (no Android types) so it is unit-testable on the JVM.
 */

/** A single rendered block inside a manual section. */
sealed interface ManualBlock {
    data class Heading(val level: Int, val text: String) : ManualBlock
    data class Paragraph(val text: String) : ManualBlock
    data class Bullet(val text: String) : ManualBlock
    /** Fenced code or a pipe-table, rendered verbatim in a monospace card. */
    data class Code(val text: String) : ManualBlock
}

/**
 * A `##`-level section of the manual. [searchText] is a lowercased flattening of
 * the title and all block text, used by [ManualSearch].
 */
data class ManualSection(
    val title: String,
    val anchor: String,
    val blocks: List<ManualBlock>,
) {
    val searchText: String = buildString {
        append(title.lowercase())
        append('\n')
        blocks.forEach { b ->
            when (b) {
                is ManualBlock.Heading -> append(b.text.lowercase())
                is ManualBlock.Paragraph -> append(b.text.lowercase())
                is ManualBlock.Bullet -> append(b.text.lowercase())
                is ManualBlock.Code -> append(b.text.lowercase())
            }
            append('\n')
        }
    }
}

/** The parsed manual: a document title plus its `##` sections in order. */
data class ManualDocument(
    val title: String,
    val sections: List<ManualSection>,
)

object ManualParser {

    /** Derive a stable, URL-ish anchor from a section title. */
    fun anchorOf(title: String): String =
        title.lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .trim('-')
            .replace(Regex("-+"), "-")

    fun parse(markdown: String): ManualDocument {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var docTitle = "Manual"
        val sections = mutableListOf<ManualSection>()

        var currentTitle: String? = null
        var currentBlocks = mutableListOf<ManualBlock>()

        // paragraph / code / table accumulators
        val paragraph = StringBuilder()
        val code = StringBuilder()
        val table = StringBuilder()
        var inCode = false

        fun flushParagraph() {
            val t = paragraph.toString().trim()
            if (t.isNotEmpty()) currentBlocks.add(ManualBlock.Paragraph(t))
            paragraph.setLength(0)
        }
        fun flushTable() {
            val t = table.toString().trimEnd('\n')
            if (t.isNotEmpty()) currentBlocks.add(ManualBlock.Code(t))
            table.setLength(0)
        }
        fun flushCode() {
            val t = code.toString().trimEnd('\n')
            currentBlocks.add(ManualBlock.Code(t))
            code.setLength(0)
        }
        fun startSection(title: String) {
            // close any previous section
            if (currentTitle != null) {
                sections.add(ManualSection(currentTitle!!, anchorOf(currentTitle!!), currentBlocks))
            }
            currentTitle = title
            currentBlocks = mutableListOf()
        }

        for (raw in lines) {
            val line = raw
            // Fenced code toggles take precedence.
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    flushCode(); inCode = false
                } else {
                    flushParagraph(); flushTable(); inCode = true
                }
                continue
            }
            if (inCode) {
                code.append(line).append('\n')
                continue
            }

            val trimmed = line.trim()

            // Table rows: consecutive lines starting with '|'.
            if (trimmed.startsWith("|")) {
                flushParagraph()
                // skip pure separator rows like |---|---|
                if (!trimmed.replace("|", "").replace("-", "").replace(":", "").isBlank()) {
                    table.append(trimmed.trim('|').split("|").joinToString("   ") { it.trim() }).append('\n')
                }
                continue
            } else if (table.isNotEmpty()) {
                flushTable()
            }

            when {
                trimmed.startsWith("#### ") -> {
                    flushParagraph()
                    currentBlocks.add(ManualBlock.Heading(4, trimmed.removePrefix("#### ").trim()))
                }
                trimmed.startsWith("### ") -> {
                    flushParagraph()
                    currentBlocks.add(ManualBlock.Heading(3, trimmed.removePrefix("### ").trim()))
                }
                trimmed.startsWith("## ") -> {
                    flushParagraph()
                    startSection(trimmed.removePrefix("## ").trim())
                }
                trimmed.startsWith("# ") -> {
                    flushParagraph()
                    docTitle = trimmed.removePrefix("# ").trim()
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    currentBlocks.add(ManualBlock.Bullet(trimmed.substring(2).trim()))
                }
                trimmed.startsWith("> ") -> {
                    flushParagraph()
                    currentBlocks.add(ManualBlock.Paragraph(trimmed.removePrefix("> ").trim()))
                }
                trimmed.isEmpty() -> flushParagraph()
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(trimmed)
                }
            }
        }
        // flush trailing accumulators
        if (inCode) flushCode()
        if (table.isNotEmpty()) flushTable()
        flushParagraph()
        if (currentTitle != null) {
            sections.add(ManualSection(currentTitle!!, anchorOf(currentTitle!!), currentBlocks))
        }
        return ManualDocument(docTitle, sections)
    }
}

object ManualSearch {
    /**
     * Filter [sections] to those matching [query]. Empty / blank query returns
     * every section. Matching is case-insensitive; every whitespace-separated
     * token in the query must appear somewhere in the section (title or body).
     */
    fun filter(sections: List<ManualSection>, query: String): List<ManualSection> {
        val tokens = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return sections
        return sections.filter { section ->
            tokens.all { token -> section.searchText.contains(token) }
        }
    }
}
