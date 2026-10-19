package com.victorious.browser.reader

import org.json.JSONObject

/**
 * Reader mode: strips a page down to its article content, in the spirit of Hermit's
 * reader view. Built on Mozilla's Readability.js (Apache-2.0, vendored verbatim at
 * `assets/readability.js` — see NOTICE), run inside the page's own WebView context
 * via `evaluateJavascript` rather than ported to Kotlin.
 *
 * This file holds the pure, unit-testable pieces: building the extraction script and
 * decoding what `evaluateJavascript`'s callback hands back. The callback value is the
 * JS expression's result JSON-encoded by WebView itself; since the script's own return
 * value is already a JSON string (from `JSON.stringify`), the callback delivers that
 * string JSON-encoded a second time, so it must be unwrapped once before the article
 * JSON is parseable.
 */
object ReaderMode {
    data class Article(val title: String, val byline: String, val contentHtml: String)

    /**
     * Runs Readability against a clone of `document` so the live page is untouched if
     * parsing fails or reader mode is toggled off. [readabilityJsSource] is the vendored
     * library's full source, inlined ahead of the call (evaluateJavascript has no module
     * loader, so there is no separate "load the library" step).
     */
    fun buildExtractionScript(readabilityJsSource: String): String = buildString {
        append("(function() {\n")
        append(readabilityJsSource)
        append(
            """
            try {
                var docClone = document.cloneNode(true);
                var article = new Readability(docClone).parse();
                if (!article) { return null; }
                return JSON.stringify({
                    title: article.title || document.title || "",
                    byline: article.byline || "",
                    content: article.content || ""
                });
            } catch (e) {
                return null;
            }
            """.trimIndent(),
        )
        append("\n})();")
    }

    /** Decodes an `evaluateJavascript` callback value; null if parsing failed or the page had no article. */
    fun parseExtractionResult(callbackValue: String?): Article? {
        if (callbackValue.isNullOrBlank() || callbackValue == "null") return null
        return try {
            val jsonString = JSONObject("{\"v\":$callbackValue}").getString("v")
            val obj = JSONObject(jsonString)
            Article(
                title = obj.optString("title"),
                byline = obj.optString("byline"),
                contentHtml = obj.optString("content"),
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Readability extracts content, it does not guarantee it is free of `<script>` tags
     * or inline event-handler attributes from the source page. [renderDocument] loads its
     * output back into the same JS-enabled WebView, so an un-sanitized script tag here
     * would execute with the page's own origin — this is a real risk for this app in
     * particular, not a defense-in-depth nicety, given the WebView already carries
     * cookies and (optionally) runs through the inspection proxy.
     *
     * This is a regex pass over already-extracted content, not a full HTML parser —
     * strips `<script>`/`<style>`/`<iframe>`/`<object>`/`<embed>` elements, `javascript:`
     * URLs, and `on*` event-handler attributes. It narrows the risk; it is not a
     * substitute for a real sanitizer (e.g. DOMPurify) if this ever needs to be hardened
     * further.
     */
    internal fun sanitizeContentHtml(html: String): String {
        var out = html
        out = Regex("(?is)<(script|style|iframe|object|embed)\\b.*?</\\1\\s*>").replace(out, "")
        out = Regex("(?is)<(script|style|iframe|object|embed)\\b[^>]*/?>").replace(out, "")
        out = Regex("(?is)\\son\\w+\\s*=\\s*\"[^\"]*\"").replace(out, "")
        out = Regex("(?is)\\son\\w+\\s*=\\s*'[^']*'").replace(out, "")
        out = Regex("(?is)(href|src)\\s*=\\s*(\"|')\\s*javascript:[^\"']*(\"|')").replace(out, "$1=$2#$3")
        return out
    }

    /**
     * A minimal styled document for [article], loaded back into the same WebView via
     * `loadDataWithBaseURL` with the original page's URL as the base (so the article's
     * own relative image/link URLs keep resolving). [dark] mirrors the Night chip so
     * reader mode doesn't fight it. [article.contentHtml] is passed through
     * [sanitizeContentHtml] first — see its doc for why that matters here specifically.
     */
    fun renderDocument(article: Article, dark: Boolean): String {
        val bg = if (dark) "#121212" else "#ffffff"
        val fg = if (dark) "#e8e8e8" else "#1a1a1a"
        val byline = if (article.byline.isNotBlank()) {
            "<p class=\"byline\">${escapeHtml(article.byline)}</p>"
        } else {
            ""
        }
        return """
            <!DOCTYPE html>
            <html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                body { background:$bg; color:$fg; font-family: sans-serif; line-height:1.5;
                       padding:16px; max-width:700px; margin:0 auto; }
                h1 { font-size:1.5em; }
                .byline { color: #888; font-style: italic; }
                img, video { max-width:100%; }
                a { color: #00e5ff; }
            </style></head>
            <body>
            <h1>${escapeHtml(article.title)}</h1>
            $byline
            ${sanitizeContentHtml(article.contentHtml)}
            </body></html>
        """.trimIndent()
    }

    private fun escapeHtml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
