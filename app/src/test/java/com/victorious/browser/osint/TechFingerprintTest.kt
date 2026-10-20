package com.victorious.browser.osint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechFingerprintTest {

    @Test
    fun detectsNginxAndPhpFromHeaders() {
        val r = TechFingerprint.analyze(
            listOf(
                "Server" to "nginx/1.24.0",
                "X-Powered-By" to "PHP/8.2.0",
                "Set-Cookie" to "PHPSESSID=abc; Path=/",
            ),
        )
        val techs = r.signals.map { it.technology }.toSet()
        assertTrue(techs.contains("nginx"))
        assertTrue(techs.contains("PHP"))
        assertEquals(TechFingerprint.Confidence.HIGH, r.signals.first { it.technology == "nginx" }.confidence)
    }

    @Test
    fun detectsWordPressFromMetaAndPaths() {
        val html = """
            <html><head>
            <meta name="generator" content="WordPress 6.4.2" />
            <link rel="stylesheet" href="/wp-content/themes/x/style.css"/>
            </head></html>
        """.trimIndent()
        val r = TechFingerprint.analyze(emptyList(), html)
        assertTrue(r.signals.any { it.technology == "WordPress" })
        assertEquals(
            TechFingerprint.Confidence.HIGH,
            r.signals.first { it.technology == "WordPress" }.confidence,
        )
    }

    @Test
    fun detectsCloudflareFromCfRay() {
        val r = TechFingerprint.analyze(listOf("CF-Ray" to "7a1b2c3d4e5f-EWR"))
        assertTrue(r.signals.any { it.technology == "Cloudflare" })
    }

    @Test
    fun detectsAspNetFromCookieAndHeader() {
        val r = TechFingerprint.analyze(
            listOf(
                "X-AspNet-Version" to "4.0.30319",
                "Set-Cookie" to "ASP.NET_SessionId=xyz; path=/",
            ),
        )
        assertTrue(r.signals.any { it.technology == "ASP.NET" })
    }

    @Test
    fun detectsNextJsFromHtmlMarker() {
        val html = """<script id="__NEXT_DATA__" type="application/json">{}</script>
            <link href="/_next/static/css/app.css"/>"""
        val r = TechFingerprint.analyze(emptyList(), html)
        assertTrue(r.signals.any { it.technology == "Next.js" })
    }

    @Test
    fun detectsLaravelFromCookie() {
        val r = TechFingerprint.analyze(
            listOf("Set-Cookie" to "laravel_session=abc123; Path=/; HttpOnly"),
        )
        assertTrue(r.signals.any { it.technology == "Laravel (PHP)" })
    }

    @Test
    fun metaGenerator_parsesBothAttributeOrders() {
        assertEquals(
            "Hugo 0.120",
            TechFingerprint.metaGenerator("""<meta name="generator" content="Hugo 0.120">"""),
        )
        assertEquals(
            "Ghost 5.0",
            TechFingerprint.metaGenerator("""<meta content="Ghost 5.0" name="generator"/>"""),
        )
        assertEquals(null, TechFingerprint.metaGenerator("<html></html>"))
    }

    @Test
    fun emptyInput_yieldsNoSignals() {
        assertTrue(TechFingerprint.analyze(emptyList(), null).isEmpty)
        assertTrue(TechFingerprint.analyze(emptyList(), "").isEmpty)
    }

    @Test
    fun dedupesPreferringHigherConfidence() {
        val html = """<meta name="generator" content="WordPress 6.0">
            <link href="/wp-content/x.css"/>"""
        val r = TechFingerprint.analyze(
            listOf("X-Generator" to "WordPress 6.0"),
            html,
        )
        val wp = r.signals.filter { it.technology == "WordPress" }
        assertEquals(1, wp.size)
        assertEquals(TechFingerprint.Confidence.HIGH, wp[0].confidence)
    }

    @Test
    fun detectsGoogleTagManager() {
        val html = """<script src="https://www.googletagmanager.com/gtag/js?id=G-XXX"></script>
            <script>gtag('config','G-XXX');</script>"""
        val r = TechFingerprint.analyze(emptyList(), html)
        assertTrue(r.signals.any { it.technology == "Google Tag Manager" })
    }
}
