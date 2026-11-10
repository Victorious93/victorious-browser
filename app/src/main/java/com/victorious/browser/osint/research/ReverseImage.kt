package com.victorious.browser.osint.research

/**
 * Reverse image search via each engine's public, user-facing pages.
 *
 *  - Image URL → the engine's documented search-by-URL link, opened in a tab.
 *  - Local file → the engine's upload page, opened in a tab; the user picks the file in
 *    the page's own upload button (WebView file chooser). No private/undocumented APIs
 *    are called and no image bytes are sent anywhere by this app.
 *
 * Face-recognition search services are intentionally not included.
 */
object ReverseImage {
    enum class Engine(val label: String, val byUrl: String, val uploadPage: String) {
        GOOGLE_LENS("Google Lens", "https://lens.google.com/uploadbyurl?url={url}", "https://lens.google.com/"),
        BING("Bing Visual Search", "https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:{url}", "https://www.bing.com/visualsearch"),
        YANDEX("Yandex Images", "https://yandex.com/images/search?rpt=imageview&url={url}", "https://yandex.com/images/"),
        TINEYE("TinEye", "https://tineye.com/search?url={url}", "https://tineye.com/"),
    }

    /** Only http(s) image URLs can be handed to an engine (no data:, blob:, file:, content:). */
    fun isShareableImageUrl(url: String?): Boolean {
        val u = url?.trim() ?: return false
        if (u.length > 4096 || u.any { it.isWhitespace() }) return false
        return (u.startsWith("https://", true) || u.startsWith("http://", true)) && u.substringAfter("://").isNotEmpty()
    }

    fun byUrl(engine: Engine, imageUrl: String): String? =
        if (!isShareableImageUrl(imageUrl)) null else engine.byUrl.replace("{url}", DorkComposer.encode(imageUrl.trim()))

    fun uploadPage(engine: Engine): String = engine.uploadPage

    /** Custom engine: http(s) template containing `{url}`. */
    fun isValidCustomTemplate(t: String): Boolean {
        val s = t.trim()
        return (s.startsWith("https://", true) || s.startsWith("http://", true)) && s.contains("{url}")
    }

    fun custom(template: String, imageUrl: String): String? {
        if (!isValidCustomTemplate(template) || !isShareableImageUrl(imageUrl)) return null
        return template.trim().replace("{url}", DorkComposer.encode(imageUrl.trim()))
    }

    /** All by-URL links for [imageUrl] (plus custom when valid). */
    fun allByUrl(imageUrl: String, customTemplate: String = ""): List<Pivot> = buildList {
        Engine.entries.forEach { e -> byUrl(e, imageUrl)?.let { add(Pivot(e.label, it)) } }
        custom(customTemplate, imageUrl)?.let { add(Pivot("Custom", it)) }
    }
}
