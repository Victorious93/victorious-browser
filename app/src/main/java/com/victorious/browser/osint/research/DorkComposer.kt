package com.victorious.browser.osint.research

import java.net.URLEncoder

/**
 * Pure dork-query composer for the Deep Research hub.
 *
 * Builds a query from operator chips and wraps it in a search URL for Google, Bing,
 * DuckDuckGo or a user-defined engine template. Nothing here touches the network:
 * the operator reviews the query and the app opens it in a normal browser tab.
 */
object DorkComposer {

    enum class Engine(val label: String, val template: String) {
        GOOGLE("Google", "https://www.google.com/search?q=%s"),
        BING("Bing", "https://www.bing.com/search?q=%s"),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
        CUSTOM("Custom", ""),
    }

    /** Operator chips. [valued] operators take a `name:value` argument. */
    enum class Op(val label: String, val keyword: String?, val hint: String) {
        SITE("site:", "site", "example.com"),
        INURL("inurl:", "inurl", "admin"),
        INTITLE("intitle:", "intitle", "index of"),
        INTEXT("intext:", "intext", "password"),
        FILETYPE("filetype:", "filetype", "pdf"),
        EXT("ext:", "ext", "sql"),
        BEFORE("before:", "before", "2024-01-01"),
        AFTER("after:", "after", "2023-01-01"),
        EXACT("\"…\"", null, "exact phrase"),
        EXCLUDE("-", null, "term or op:value"),
        OR("OR", null, ""),
        TERM("keyword", null, "free text");
    }

    private val DATE = Regex("^\\d{4}(-\\d{2}(-\\d{2})?)?$")

    /** Format one chip into a query token. Returns "" for an unusable value. */
    fun token(op: Op, value: String = ""): String {
        val v = value.trim()
        return when (op) {
            Op.OR -> "OR"
            Op.EXACT -> if (v.isEmpty()) "" else "\"${v.replace("\"", "")}\""
            Op.EXCLUDE -> if (v.isEmpty()) "" else "-" + v.removePrefix("-")
            Op.TERM -> v
            Op.BEFORE, Op.AFTER -> if (DATE.matches(v)) "${op.keyword}:$v" else ""
            else -> {
                if (v.isEmpty()) return ""
                val rendered = if (v.any { it.isWhitespace() }) "\"${v.replace("\"", "")}\"" else v
                "${op.keyword}:$rendered"
            }
        }
    }

    /** Join tokens; drops blanks and dangling / doubled ORs. */
    fun build(tokens: List<String>): String {
        val cleaned = tokens.map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        for (t in cleaned) {
            if (t == "OR" && (out.isEmpty() || out.last() == "OR")) continue
            out += t
        }
        while (out.lastOrNull() == "OR") out.removeAt(out.size - 1)
        return out.joinToString(" ").replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Custom templates must be http(s) and contain `%s` (or `{q}`) where the encoded
     * query goes.
     */
    fun isValidCustomTemplate(t: String): Boolean {
        val s = t.trim()
        return (s.startsWith("https://", true) || s.startsWith("http://", true)) &&
            (s.contains("%s") || s.contains("{q}"))
    }

    /** Search URL for [query]; null for a blank query or invalid custom template. */
    fun searchUrl(engine: Engine, query: String, customTemplate: String = ""): String? {
        if (query.isBlank()) return null
        val tpl = if (engine == Engine.CUSTOM) customTemplate.trim() else engine.template
        if (engine == Engine.CUSTOM && !isValidCustomTemplate(tpl)) return null
        val enc = encode(query.trim())
        return tpl.replace("{q}", enc).replace("%s", enc)
    }

    fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}

/**
 * Curated, recon-focused dork templates. `{d}` = target domain, `{k}` = keyword.
 * Templates needing a placeholder that the user left blank are not expanded.
 */
object DorkLibrary {
    data class Dork(val category: String, val name: String, val template: String) {
        val needsDomain get() = template.contains("{d}")
        val needsKeyword get() = template.contains("{k}")
    }

    val all: List<Dork> = listOf(
        // Exposed files
        Dork("Exposed files", "Office & PDF docs", "site:{d} (filetype:pdf OR filetype:docx OR filetype:xlsx OR filetype:pptx)"),
        Dork("Exposed files", "Spreadsheets / CSV", "site:{d} (filetype:xls OR filetype:xlsx OR filetype:csv)"),
        Dork("Exposed files", "Logs", "site:{d} ext:log"),
        Dork("Exposed files", "Text files mentioning password", "site:{d} ext:txt intext:password"),
        // Login pages
        Dork("Login pages", "Login / sign-in", "site:{d} (inurl:login OR inurl:signin OR intitle:login)"),
        Dork("Login pages", "Admin panels", "site:{d} (inurl:admin OR intitle:admin OR inurl:dashboard)"),
        Dork("Login pages", "Webmail / portals", "site:{d} (inurl:webmail OR inurl:portal OR inurl:owa)"),
        // Directory listings
        Dork("Directory listings", "Open index pages", "site:{d} intitle:\"index of\""),
        Dork("Directory listings", "Parent directory", "site:{d} intitle:\"index of\" \"parent directory\""),
        Dork("Directory listings", "Backup folders", "site:{d} intitle:\"index of\" (backup OR bak OR old)"),
        // Config / backup
        Dork("Config & backup", "Env / config files", "site:{d} (ext:env OR ext:ini OR ext:conf OR ext:cfg OR ext:yml)"),
        Dork("Config & backup", "Backups & dumps", "site:{d} (ext:bak OR ext:old OR ext:backup OR ext:sql OR ext:zip)"),
        Dork("Config & backup", "Git / SVN metadata", "site:{d} (inurl:\".git\" OR inurl:\".svn\")"),
        Dork("Config & backup", "PHP info pages", "site:{d} ext:php intitle:phpinfo"),
        // Errors
        Dork("Errors & debug", "SQL errors", "site:{d} (intext:\"sql syntax\" OR intext:\"mysql_fetch\" OR intext:\"ORA-\")"),
        Dork("Errors & debug", "Stack traces", "site:{d} (intext:\"stack trace\" OR intext:\"Traceback (most recent call last)\")"),
        // Docs & people
        Dork("Docs & people", "Keyword in documents", "site:{d} {k} (filetype:pdf OR filetype:docx)"),
        Dork("Docs & people", "Employee profiles", "site:linkedin.com/in \"{k}\""),
        Dork("Docs & people", "Mentions off-site", "\"{d}\" -site:{d}"),
        // Subdomains & infrastructure
        Dork("Subdomains", "Subdomains (exclude www)", "site:*.{d} -site:www.{d}"),
        Dork("Subdomains", "Dev / staging hosts", "site:{d} (inurl:dev OR inurl:staging OR inurl:test OR inurl:uat)"),
        Dork("Subdomains", "API endpoints", "site:{d} (inurl:api OR inurl:swagger OR inurl:graphql)"),
        // Code & paste
        Dork("Code & paste", "Pastebin mentions", "site:pastebin.com \"{d}\""),
        Dork("Code & paste", "GitHub mentions", "site:github.com \"{d}\""),
        Dork("Code & paste", "Trello / Notion boards", "(site:trello.com OR site:notion.site) \"{k}\""),
        // Cameras & devices (indexed public pages only)
        Dork("Cameras & devices", "Webcam viewer pages", "site:{d} (intitle:\"webcam\" OR inurl:\"view/view.shtml\" OR intitle:\"live view\")"),
        Dork("Cameras & devices", "Printer / device panels", "site:{d} (intitle:\"printer status\" OR intitle:\"web image monitor\")"),
        // Cloud
        Dork("Cloud storage", "S3 buckets mentioning target", "site:s3.amazonaws.com \"{k}\""),
        Dork("Cloud storage", "Shared Google Docs", "site:docs.google.com \"{k}\""),
    )

    val categories: List<String> get() = all.map { it.category }.distinct()

    /** Expand a template; null when a needed placeholder is blank. */
    fun expand(dork: Dork, domain: String, keyword: String): String? {
        val d = domain.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        val k = keyword.trim().replace("\"", "")
        if (dork.needsDomain && d.isEmpty()) return null
        if (dork.needsKeyword && k.isEmpty()) return null
        return dork.template.replace("{d}", d).replace("{k}", k)
    }
}
