package com.victorious.browser.fuzz

/**
 * A minimal, hand-rolled YAML-ish parser — NOT a full YAML implementation.
 *
 * Rationale: Don's rules forbid adding dependencies for something this small, so
 * rather than pull in snakeyaml we parse the tiny subset the template engine
 * needs: indentation-based block mappings, block sequences ("- item"),
 * scalars, "key: value" pairs, quoted strings, and block sequences of mappings.
 * Flow collections ({}, []), anchors, multi-doc streams, folded/literal block
 * scalars (>, |) and tags are intentionally unsupported.
 *
 * Fully unit-tested in [MiniYamlTest]; no Android/IO types.
 */
object MiniYaml {

    sealed interface Node
    data class Scalar(val value: String) : Node
    data class Mapping(val entries: LinkedHashMap<String, Node>) : Node
    data class Sequence(val items: List<Node>) : Node

    class ParseException(message: String) : Exception(message)

    private data class Line(val indent: Int, val content: String)

    fun parse(text: String): Node {
        val lines = text.split("\n")
            .map { stripComment(it) }
            .map { it ->
                val trimmedRight = it.trimEnd()
                val indent = trimmedRight.length - trimmedRight.trimStart(' ').length
                Line(indent, trimmedRight.trim())
            }
            .filter { it.content.isNotEmpty() }
        if (lines.isEmpty()) return Mapping(LinkedHashMap())
        val (node, next) = parseBlock(lines, 0, lines.first().indent)
        if (next != lines.size) {
            // Trailing content at a shallower indent than expected — tolerate by ignoring.
        }
        return node
    }

    /** Strip a trailing/full-line comment, leaving quoted "#" alone. */
    private fun stripComment(line: String): String {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                c == '#' && !inSingle && !inDouble -> {
                    // Only treat as comment if at line start or preceded by whitespace.
                    if (i == 0 || line[i - 1] == ' ' || line[i - 1] == '\t') {
                        return line.substring(0, i)
                    }
                }
            }
            i++
        }
        return line
    }

    /** Parse a block starting at [start] whose items sit at [indent]. Returns (node, nextIndex). */
    private fun parseBlock(lines: List<Line>, start: Int, indent: Int): Pair<Node, Int> {
        return if (lines[start].content.startsWith("- ")) {
            parseSequence(lines, start, indent)
        } else if (lines[start].content == "-") {
            parseSequence(lines, start, indent)
        } else {
            parseMapping(lines, start, indent)
        }
    }

    private fun parseMapping(lines: List<Line>, start: Int, indent: Int): Pair<Node, Int> {
        val map = LinkedHashMap<String, Node>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.indent < indent) break
            if (line.indent > indent) throw ParseException("unexpected indent at: ${line.content}")
            val colon = findColon(line.content)
                ?: throw ParseException("expected 'key:' at: ${line.content}")
            val key = unquote(line.content.substring(0, colon).trim())
            val rest = line.content.substring(colon + 1).trim()
            if (rest.isNotEmpty()) {
                map[key] = Scalar(unquote(rest))
                i++
            } else {
                // Child block on following deeper lines (mapping or sequence).
                if (i + 1 < lines.size && lines[i + 1].indent > indent) {
                    val childIndent = lines[i + 1].indent
                    val (child, next) = parseBlock(lines, i + 1, childIndent)
                    map[key] = child
                    i = next
                } else if (i + 1 < lines.size && lines[i + 1].indent == indent &&
                    lines[i + 1].content.startsWith("- ")
                ) {
                    // Sequence items at the same indent as the key (common YAML style).
                    val (child, next) = parseSequence(lines, i + 1, indent)
                    map[key] = child
                    i = next
                } else {
                    map[key] = Scalar("")
                    i++
                }
            }
        }
        return Mapping(map) to i
    }

    private fun parseSequence(lines: List<Line>, start: Int, indent: Int): Pair<Node, Int> {
        val items = ArrayList<Node>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            if (line.indent < indent) break
            if (line.indent > indent) throw ParseException("unexpected indent in sequence at: ${line.content}")
            if (!line.content.startsWith("-")) break
            val afterDash = line.content.removePrefix("-").trim()
            if (afterDash.isEmpty()) {
                // Item content on deeper following lines.
                if (i + 1 < lines.size && lines[i + 1].indent > indent) {
                    val childIndent = lines[i + 1].indent
                    val (child, next) = parseBlock(lines, i + 1, childIndent)
                    items.add(child)
                    i = next
                } else {
                    items.add(Scalar(""))
                    i++
                }
            } else if (findColon(afterDash) != null) {
                // Inline mapping start: "- key: value". The virtual mapping sits at
                // the column where afterDash begins; gather its continuation lines.
                val virtualIndent = line.indent + (line.content.indexOf(afterDash))
                val synthetic = ArrayList<Line>()
                synthetic.add(Line(virtualIndent, afterDash))
                var j = i + 1
                while (j < lines.size && lines[j].indent > indent) {
                    synthetic.add(lines[j])
                    j++
                }
                val (child, _) = parseMapping(synthetic, 0, virtualIndent)
                items.add(child)
                i = j
            } else {
                items.add(Scalar(unquote(afterDash)))
                i++
            }
        }
        return Sequence(items) to i
    }

    /** Find the colon that separates key from value (first ": " or trailing ":"). */
    private fun findColon(s: String): Int? {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\'' && !inDouble -> inSingle = !inSingle
                c == '"' && !inSingle -> inDouble = !inDouble
                c == ':' && !inSingle && !inDouble -> {
                    if (i == s.length - 1 || s[i + 1] == ' ') return i
                }
            }
            i++
        }
        return null
    }

    private fun unquote(s: String): String {
        val t = s.trim()
        if (t.length >= 2) {
            if (t.startsWith("\"") && t.endsWith("\"")) {
                return t.substring(1, t.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")
            }
            if (t.startsWith("'") && t.endsWith("'")) {
                return t.substring(1, t.length - 1).replace("''", "'")
            }
        }
        return t
    }

    // --- Convenience accessors ---------------------------------------------------

    fun Node.asMapping(): Mapping? = this as? Mapping
    fun Node.asSequence(): Sequence? = this as? Sequence
    fun Node.asScalar(): String? = (this as? Scalar)?.value

    fun Mapping.str(key: String): String? = (entries[key] as? Scalar)?.value
    fun Mapping.node(key: String): Node? = entries[key]
}
