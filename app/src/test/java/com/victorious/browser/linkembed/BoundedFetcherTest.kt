package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class BoundedFetcherTest {
    @Test fun followsRedirectsAndReportsChain() {
        val t = FakeTransport(mapOf(
            "https://a.example/" to FakeTransport.redirect("/b", 301),
            "https://a.example/b" to FakeTransport.redirect("https://c.example/final", 302),
            "https://c.example/final" to FakeTransport.ok("<title>x</title>"),
        ))
        val r = BoundedFetcher(t).fetch("https://a.example/")
        assertTrue(r.ok)
        assertEquals(200, r.status)
        assertEquals("https://c.example/final", r.finalUrl)
        assertEquals(listOf("https://a.example/b", "https://c.example/final"), r.redirects)
        assertEquals("<title>x</title>", r.text())
    }

    @Test fun capsRedirects() {
        val routes = (0..10).associate { i -> "https://a.example/$i" to FakeTransport.redirect("/${i + 1}") }
        val r = BoundedFetcher(FakeTransport(routes), FetchLimits(maxRedirects = 3)).fetch("https://a.example/0")
        assertFalse(r.ok)
        assertTrue(r.error!!.contains("Too many redirects"))
        assertEquals(3, r.redirects.size)
    }

    @Test fun detectsLoops() {
        val t = FakeTransport(mapOf(
            "https://a.example/1" to FakeTransport.redirect("/2"),
            "https://a.example/2" to FakeTransport.redirect("/1"),
        ))
        val r = BoundedFetcher(t).fetch("https://a.example/1")
        assertEquals("Redirect loop", r.error)
    }

    @Test fun blocksRedirectToUnsafeScheme() {
        for (target in listOf("javascript:alert(1)", "file:///etc/passwd", "content://x/y", "data:text/html,hi")) {
            val t = FakeTransport(mapOf("https://a.example/" to FakeTransport.redirect(target)))
            val r = BoundedFetcher(t).fetch("https://a.example/")
            assertFalse(target, r.ok)
            assertTrue(target, r.error!!.contains("blocked"))
            assertEquals(1, t.requests.size)
        }
    }

    @Test fun rejectsUnsafeStartWithoutNetwork() {
        val t = FakeTransport(emptyMap())
        val r = BoundedFetcher(t).fetch("file:///sdcard/secret")
        assertFalse(r.ok)
        assertTrue(t.requests.isEmpty())
    }

    @Test fun truncatesAtMaxBytes() {
        val body = ByteArray(10_000) { 'a'.code.toByte() }
        val t = FakeTransport(mapOf("https://a.example/" to { FakeTransport.bytes(body, "text/html") }))
        val r = BoundedFetcher(t).fetch("https://a.example/", maxBytes = 1000)
        assertTrue(r.ok)
        assertTrue(r.truncated)
        assertEquals(1000, r.body.size)
    }

    @Test fun skipsBodyWhenContentLengthTooLarge() {
        var read = false
        val stream = object : InputStream() { override fun read(): Int { read = true; return -1 } }
        val t = FakeTransport(mapOf("https://a.example/" to {
            TransportResponse(200, mapOf("Content-Length" to listOf("99999999"), "Content-Type" to listOf("image/png")), stream)
        }))
        val r = BoundedFetcher(t).fetch("https://a.example/", maxBytes = 1000)
        assertTrue(r.truncated)
        assertEquals(0, r.body.size)
        assertFalse(read)
    }

    @Test fun exactlyMaxBytesIsNotTruncated() {
        val t = FakeTransport(mapOf("https://a.example/" to { FakeTransport.bytes(ByteArray(1000), "text/plain") }))
        val r = BoundedFetcher(t).fetch("https://a.example/", maxBytes = 1000)
        assertFalse(r.truncated)
        assertEquals(1000, r.body.size)
    }

    @Test fun overallDeadline() {
        var now = 0L
        val t = FakeTransport(mapOf(
            "https://a.example/" to { now += 20_000; FakeTransport.redirect("/b")() },
            "https://a.example/b" to FakeTransport.ok("x"),
        ))
        val r = BoundedFetcher(t, FetchLimits(totalTimeoutMs = 15_000), clock = { now }).fetch("https://a.example/")
        assertEquals("Timed out", r.error)
    }

    @Test fun privacyRewriteAppliedToEveryHop() {
        val t = FakeTransport(mapOf(
            "https://a.example/" to FakeTransport.redirect("http://b.example/x"),
            "https://b.example/x" to FakeTransport.ok("ok"),
        ))
        val r = BoundedFetcher(t, rewrite = { it.replace("http://", "https://") }).fetch("http://a.example/")
        assertTrue(r.ok)
        assertEquals(listOf("https://a.example/", "https://b.example/x"), t.requests.map { it.first })
    }

    @Test fun transportErrorReported() {
        val r = BoundedFetcher(FakeTransport(emptyMap())).fetch("https://down.example/")
        assertFalse(r.ok)
        assertNotNull(r.error)
        assertNull(r.status)
    }

    @Test fun charsetFromContentType() {
        val t = FakeTransport(mapOf("https://a.example/" to {
            FakeTransport.bytes("caf\u00e9".toByteArray(Charsets.ISO_8859_1), "text/html; charset=ISO-8859-1")
        }))
        assertEquals("caf\u00e9", BoundedFetcher(t).fetch("https://a.example/").text())
    }

    @Test fun nullBodyOk() {
        val t = FakeTransport(mapOf("https://a.example/" to { TransportResponse(204, emptyMap(), null) }))
        val r = BoundedFetcher(t).fetch("https://a.example/")
        assertEquals(204, r.status)
        assertEquals(0, r.body.size)
    }

    @Suppress("unused")
    private fun stream(s: String): InputStream = ByteArrayInputStream(s.toByteArray())
}
