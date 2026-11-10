package com.victorious.browser.linkembed

/**
 * Decides what the sandboxed embed viewer may show for a preview, and builds the
 * wrapper document it loads. Pure Kotlin (unit-tested).
 *
 * The wrapper is generated entirely by us: it contains no script, carries a strict
 * CSP, and only ever hosts remote content inside a `sandbox`ed `<iframe>`:
 *  - a single provider `<iframe src=https://…>` from oEmbed, or the page itself when
 *    recon says it is embeddable → cross-origin iframe with
 *    `allow-scripts allow-same-origin allow-presentation allow-popups` (the frame keeps
 *    its own origin, which is never the app's);
 *  - any other oEmbed HTML (e.g. markup + an external widget script) → `srcdoc`
 *    iframe with `allow-scripts allow-popups` but WITHOUT `allow-same-origin`, so it
 *    runs in an opaque origin with no storage or cookies.
 * Pages that are not embeddable are never framed; the viewer says so and offers a tab.
 *
 * Both iframes carry `allow="fullscreen"` + `allowfullscreen` so a player's fullscreen
 * button reaches WebChromeClient.onShowCustomView (handled by [EmbedViewerDialog]).
 * Fullscreen grants no extra privileges: no storage, no top navigation, no bridges.
 */
object EmbedViewerHtml {
    const val BASE_URL = "https://embed-viewer.victorious.invalid/"
    const val VIEWER_HOST = "embed-viewer.victorious.invalid"

    sealed class Plan {
        data class FrameUrl(val src: String, val viaOEmbed: Boolean) : Plan()
        data class ProviderHtml(val html: String) : Plan()
        data class NotEmbeddable(val reason: String) : Plan()
    }

    const val CSP =
        "default-src 'none'; frame-src https:; script-src https:; img-src https: data:; " +
            "style-src 'unsafe-inline' https:; media-src https:; font-src https:; connect-src https:; " +
            "base-uri 'none'; form-action 'none'"

    fun plan(preview: LinkPreview): Plan {
        val html = preview.oembed?.html
        if (html != null) {
            OEmbed.singleIframeSrc(html)?.let { return Plan.FrameUrl(it, viaOEmbed = true) }
            return Plan.ProviderHtml(html)
        }
        val target = UrlSafety.sanitize(preview.recon.finalUrl ?: preview.requestedUrl)
            ?: return Plan.NotEmbeddable("This URL is not a safe http(s) address.")
        return when (preview.recon.verdict) {
            EmbedRecon.Verdict.EMBEDDABLE ->
                if (target.startsWith("https://")) Plan.FrameUrl(target, viaOEmbed = false)
                else Plan.NotEmbeddable("Plain-http pages are not framed in the viewer (mixed content). Open it as a tab instead.")
            EmbedRecon.Verdict.NOT_EMBEDDABLE ->
                Plan.NotEmbeddable("This page refuses to be framed by other sites (${preview.recon.reasons.firstOrNull() ?: "X-Frame-Options / CSP"}). Open it as a normal tab instead.")
            EmbedRecon.Verdict.CONDITIONAL ->
                Plan.NotEmbeddable("This page only allows specific sites to frame it, and the in-app viewer is not one of them. Open it as a normal tab instead.")
            EmbedRecon.Verdict.UNKNOWN ->
                Plan.NotEmbeddable("Embeddability could not be determined (${preview.recon.error ?: "no usable response"}). Open it as a normal tab instead.")
        }
    }

    /** The complete wrapper document for [plan], or null for [Plan.NotEmbeddable]. */
    fun document(plan: Plan, title: String): String? {
        val frame = when (plan) {
            is Plan.FrameUrl ->
                "<iframe src=\"${EmbedSnippets.escapeHtml(plan.src)}\" title=\"${EmbedSnippets.escapeHtml(title)}\" " +
                    "sandbox=\"allow-scripts allow-same-origin allow-presentation allow-popups\" " +
                    "allow=\"encrypted-media; picture-in-picture; fullscreen\" allowfullscreen " +
                    "referrerpolicy=\"strict-origin-when-cross-origin\"></iframe>"
            is Plan.ProviderHtml ->
                "<iframe srcdoc=\"${EmbedSnippets.escapeHtml(providerDoc(plan.html))}\" title=\"${EmbedSnippets.escapeHtml(title)}\" " +
                    "sandbox=\"allow-scripts allow-popups\" allow=\"fullscreen\" allowfullscreen referrerpolicy=\"no-referrer\"></iframe>"
            is Plan.NotEmbeddable -> return null
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta http-equiv=\"Content-Security-Policy\" content=\"${EmbedSnippets.escapeHtml(CSP)}\">" +
            "<meta name=\"referrer\" content=\"strict-origin-when-cross-origin\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>html,body{margin:0;height:100%;background:#0B0F1A}" +
            "iframe{display:block;border:0;width:100%;height:100%;background:#0B0F1A}</style>" +
            "</head><body>$frame</body></html>"
    }

    private fun providerDoc(html: String): String =
        "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<style>body{margin:0;background:#0B0F1A;color:#E6F7FF}iframe,img,video{max-width:100%}</style>" +
            "</head><body>$html</body></html>"
}
