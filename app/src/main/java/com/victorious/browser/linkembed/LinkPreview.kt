package com.victorious.browser.linkembed

/** Everything the preview sheet shows for one URL. */
data class LinkPreview(
    val requestedUrl: String,
    val finalUrl: String?,
    val metadata: LinkMetadata?,
    val oembed: OEmbedData?,
    val recon: EmbedRecon.Report,
    /** Preview image bytes (already type- and size-checked), or null. */
    val imageBytes: ByteArray?,
    val imageUrl: String?,
    /** Why no image was loaded, when there was a candidate. */
    val imageNote: String?,
    val truncated: Boolean,
    val error: String?,
    val fetchedAt: Long,
) {
    val displayTitle: String
        get() = oembed?.title ?: metadata?.title ?: finalUrl ?: requestedUrl
    val displaySite: String?
        get() = metadata?.siteName ?: oembed?.providerName ?: UrlSafety.hostOf(finalUrl ?: requestedUrl)
    val approxBytes: Int
        get() = (imageBytes?.size ?: 0) + (oembed?.html?.length ?: 0) * 2 + 4096

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Privacy rules for preview fetching. Pure (unit-tested).
 *
 * Normal tabs fetch preview metadata as soon as the preview sheet opens. Private tabs
 * NEVER fetch automatically — the sheet shows a "Load preview" button and nothing goes
 * on the network until the user taps it — and their results are never written to the
 * shared in-memory cache.
 */
object PreviewPolicy {
    fun shouldAutoFetch(isPrivate: Boolean): Boolean = !isPrivate
    fun mayFetch(isPrivate: Boolean, userTapped: Boolean): Boolean = !isPrivate || userTapped
    fun mayCache(isPrivate: Boolean): Boolean = !isPrivate
}

/**
 * Small in-memory LRU for previews: bounded by entry count, total approximate bytes,
 * and a TTL. Nothing is persisted to disk. Thread-safe.
 */
class PreviewCache(
    private val maxEntries: Int = 32,
    private val maxTotalBytes: Int = 8 * 1024 * 1024,
    private val ttlMs: Long = 30 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val map = LinkedHashMap<String, LinkPreview>(16, 0.75f, true)

    @Synchronized
    fun get(url: String): LinkPreview? {
        val hit = map[url] ?: return null
        if (clock() - hit.fetchedAt > ttlMs) {
            map.remove(url)
            return null
        }
        return hit
    }

    @Synchronized
    fun put(url: String, preview: LinkPreview) {
        if (preview.approxBytes > maxTotalBytes) return
        map.remove(url)
        map[url] = preview
        evict()
    }

    @Synchronized
    fun clear() = map.clear()

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun totalBytes(): Int = map.values.sumOf { it.approxBytes }

    private fun evict() {
        val now = clock()
        map.entries.removeAll { now - it.value.fetchedAt > ttlMs }
        val it = map.entries.iterator()
        while ((map.size > maxEntries || totalBytes() > maxTotalBytes) && it.hasNext()) {
            it.next(); it.remove()
        }
    }
}

/**
 * Orchestrates one preview: page fetch → embed recon → metadata → oEmbed → image.
 * Pure JVM; network goes through the injected [BoundedFetcher].
 */
class LinkPreviewService(
    private val fetcher: BoundedFetcher,
    private val baseHeaders: Map<String, String> = emptyMap(),
    private val loadImages: Boolean = true,
    /** True when the user's tracker blocking would block this host (images are skipped). */
    private val isBlockedHost: (String) -> Boolean = { false },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun load(url: String): LinkPreview {
        val safe = UrlSafety.check(url)
        if (safe is UrlSafety.Result.Rejected) {
            val recon = EmbedRecon.evaluate(url, null, null, emptyMap(), error = safe.reason)
            return LinkPreview(url, null, null, null, recon, null, null, null, false, safe.reason, clock())
        }
        val start = (safe as UrlSafety.Result.Ok).url
        val page = fetcher.fetch(start, baseHeaders + ("Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5"), MAX_PAGE_BYTES)
        var recon = EmbedRecon.evaluate(start, page.finalUrl, page.status, page.headers, page.redirects, page.error)
        val finalUrl = page.finalUrl ?: start
        val ct = page.contentType?.lowercase().orEmpty()

        val meta: LinkMetadata? = when {
            !page.ok -> null
            ct.startsWith("image/") -> LinkMetadata(
                url = finalUrl,
                title = finalUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() },
                imageUrl = finalUrl,
                titleSource = "url",
            )
            ct.isEmpty() || ct.contains("html") || ct.startsWith("text/") ->
                HtmlMetadataParser.parse(page.text(), finalUrl)
            else -> LinkMetadata(url = finalUrl)
        }

        if (meta?.metaCsp?.isNotEmpty() == true) recon = EmbedRecon.withMetaCspNotes(recon, meta.metaCsp)
        val oembed = if (page.ok) loadOEmbed(meta?.oembedUrl ?: OEmbed.knownEndpoint(finalUrl) ?: OEmbed.knownEndpoint(start)) else null

        val candidate = oembed?.photoUrl ?: meta?.imageUrl ?: oembed?.thumbnailUrl
        var imageBytes: ByteArray? = null
        var imageNote: String? = null
        if (candidate != null) {
            val host = UrlSafety.hostOf(candidate)
            when {
                !loadImages -> imageNote = "Image not loaded"
                host != null && isBlockedHost(host) -> imageNote = "Image host blocked by your tracker protection"
                else -> {
                    val img = fetcher.fetch(candidate, baseHeaders + ("Accept" to "image/avif,image/webp,image/png,image/jpeg,image/gif;q=0.8"), MAX_IMAGE_BYTES)
                    val ict = img.contentType?.lowercase()?.substringBefore(';')?.trim().orEmpty()
                    imageNote = when {
                        !img.ok -> "Image failed: ${img.error ?: "HTTP ${img.status}"}"
                        img.status !in 200..299 -> "Image failed: HTTP ${img.status}"
                        img.truncated -> "Image larger than ${MAX_IMAGE_BYTES / 1024} KB; skipped"
                        !ict.startsWith("image/") -> "Image skipped: content-type \"$ict\""
                        ict.contains("svg") -> "SVG images are not rendered (may contain script)"
                        else -> { imageBytes = img.body; null }
                    }
                }
            }
        }

        return LinkPreview(
            requestedUrl = start,
            finalUrl = page.finalUrl,
            metadata = meta,
            oembed = oembed,
            recon = recon,
            imageBytes = imageBytes,
            imageUrl = candidate,
            imageNote = imageNote,
            truncated = page.truncated,
            error = page.error ?: page.status?.takeIf { it !in 200..299 }?.let { "HTTP $it" },
            fetchedAt = clock(),
        )
    }

    private fun loadOEmbed(endpoint: String?): OEmbedData? {
        val ep = UrlSafety.sanitize(endpoint) ?: return null
        val r = fetcher.fetch(ep, baseHeaders + ("Accept" to "application/json"), MAX_OEMBED_BYTES)
        if (!r.ok || r.status !in 200..299 || r.truncated) return null
        return OEmbed.parse(r.text(), r.finalUrl ?: ep)
    }

    companion object {
        const val MAX_PAGE_BYTES = 512 * 1024
        const val MAX_OEMBED_BYTES = 64 * 1024
        const val MAX_IMAGE_BYTES = 1024 * 1024
    }
}
