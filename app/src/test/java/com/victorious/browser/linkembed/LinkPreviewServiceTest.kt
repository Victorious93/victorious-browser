package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewServiceTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    @Test fun fullPipelineWithOEmbedAndImage() {
        val t = FakeTransport(mapOf(
            "https://site.example/p" to FakeTransport.ok(
                """<head><meta property="og:title" content="Page"><meta property="og:image" content="/i.png">
                <link rel="alternate" type="application/json+oembed" href="/oembed?u=1"></head>""",
                headers = mapOf("X-Frame-Options" to "SAMEORIGIN"),
            ),
            "https://site.example/oembed?u=1" to FakeTransport.ok(
                """{"type":"rich","title":"From oEmbed","provider_name":"Site","html":"<div>hi</div>"}""",
                "application/json",
            ),
            "https://site.example/i.png" to { FakeTransport.bytes(png, "image/png") },
        ))
        val p = LinkPreviewService(BoundedFetcher(t), mapOf("User-Agent" to "UA", "Sec-GPC" to "1")).load("https://site.example/p")
        assertEquals("From oEmbed", p.displayTitle)
        assertEquals("Page", p.metadata!!.title)
        assertEquals(EmbedRecon.Verdict.NOT_EMBEDDABLE, p.recon.verdict)
        assertEquals("<div>hi</div>", p.oembed!!.html)
        assertTrue(p.imageBytes!!.contentEquals(png))
        assertNull(p.error)
        // Privacy headers sent on every request.
        assertTrue(t.requests.all { it.second["Sec-GPC"] == "1" && it.second["User-Agent"] == "UA" })
    }

    @Test fun svgAndNonImagesAreNotLoaded() {
        val t = FakeTransport(mapOf(
            "https://site.example/" to FakeTransport.ok("""<meta property="og:image" content="https://site.example/a.svg">"""),
            "https://site.example/a.svg" to FakeTransport.ok("<svg onload=alert(1)>", "image/svg+xml"),
        ))
        val p = LinkPreviewService(BoundedFetcher(t)).load("https://site.example/")
        assertNull(p.imageBytes)
        assertTrue(p.imageNote!!.contains("SVG"))
    }

    @Test fun oversizedImageSkipped() {
        val big = ByteArray(LinkPreviewService.MAX_IMAGE_BYTES + 10)
        val t = FakeTransport(mapOf(
            "https://site.example/" to FakeTransport.ok("""<meta property="og:image" content="/big.jpg">"""),
            "https://site.example/big.jpg" to { FakeTransport.bytes(big, "image/jpeg") },
        ))
        val p = LinkPreviewService(BoundedFetcher(t)).load("https://site.example/")
        assertNull(p.imageBytes)
        assertTrue(p.imageNote!!.contains("larger"))
    }

    @Test fun trackerHostImagesSkippedWithoutRequest() {
        val t = FakeTransport(mapOf(
            "https://site.example/" to FakeTransport.ok("""<meta property="og:image" content="https://tracker.example/pixel.png">"""),
        ))
        val p = LinkPreviewService(BoundedFetcher(t), isBlockedHost = { it == "tracker.example" }).load("https://site.example/")
        assertNull(p.imageBytes)
        assertEquals(1, t.requests.size)
        assertTrue(p.imageNote!!.contains("tracker"))
    }

    @Test fun unsafeInputNeverHitsNetwork() {
        val t = FakeTransport(emptyMap())
        val p = LinkPreviewService(BoundedFetcher(t)).load("javascript:alert(1)")
        assertTrue(t.requests.isEmpty())
        assertEquals(EmbedRecon.Verdict.UNKNOWN, p.recon.verdict)
        assertNotNull(p.error)
    }

    @Test fun networkFailureIsUnknown() {
        val p = LinkPreviewService(BoundedFetcher(FakeTransport(emptyMap()))).load("https://down.example/")
        assertEquals(EmbedRecon.Verdict.UNKNOWN, p.recon.verdict)
        assertNull(p.metadata)
        assertNotNull(p.error)
    }

    @Test fun privateTabsNeverAutoFetchOrCache() {
        assertFalse(PreviewPolicy.shouldAutoFetch(isPrivate = true))
        assertFalse(PreviewPolicy.mayFetch(isPrivate = true, userTapped = false))
        assertTrue(PreviewPolicy.mayFetch(isPrivate = true, userTapped = true))
        assertFalse(PreviewPolicy.mayCache(isPrivate = true))
        assertTrue(PreviewPolicy.shouldAutoFetch(isPrivate = false))
        assertTrue(PreviewPolicy.mayFetch(isPrivate = false, userTapped = false))
        assertTrue(PreviewPolicy.mayCache(isPrivate = false))
    }

    private fun preview(url: String, at: Long, bytes: Int = 0) = LinkPreview(
        url, url, null, null, EmbedRecon.evaluate(url, url, 200, emptyMap()),
        if (bytes > 0) ByteArray(bytes) else null, null, null, false, null, at,
    )

    @Test fun cacheTtlCountAndBytesBounds() {
        var now = 0L
        val c = PreviewCache(maxEntries = 2, maxTotalBytes = 20_000, ttlMs = 1000, clock = { now })
        val a = preview("a", 0)
        c.put("a", a)
        assertSame(a, c.get("a"))
        now = 1001
        assertNull(c.get("a")) // expired

        c.put("x", preview("x", now)); c.put("y", preview("y", now)); c.put("z", preview("z", now))
        assertEquals(2, c.size())
        assertNull(c.get("x")) // LRU evicted

        c.clear()
        c.put("big1", preview("big1", now, bytes = 9_000))
        c.put("big2", preview("big2", now, bytes = 9_000))
        assertTrue(c.totalBytes() <= 20_000)
        c.put("huge", preview("huge", now, bytes = 50_000)) // larger than the whole cache: not stored
        assertNull(c.get("huge"))
    }
}
