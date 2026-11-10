package com.victorious.browser.linkembed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OEmbedTest {
    private val ep = "https://www.youtube.com/oembed?format=json&url=x"

    @Test fun parsesVideo() {
        val json = """{"type":"video","version":"1.0","title":"A video","author_name":"Someone","provider_name":"YouTube",
            "provider_url":"https://www.youtube.com/","thumbnail_url":"https://i.ytimg.com/vi/abc/hqdefault.jpg",
            "width":200,"height":"113","html":"<iframe width=\"200\" height=\"113\" src=\"https://www.youtube.com/embed/abc?feature=oembed\" frameborder=\"0\" allowfullscreen></iframe>"}"""
        val o = OEmbed.parse(json, ep)!!
        assertEquals("video", o.type)
        assertEquals("A video", o.title)
        assertEquals("YouTube", o.providerName)
        assertEquals(200, o.width)
        assertEquals(113, o.height)
        assertEquals("https://i.ytimg.com/vi/abc/hqdefault.jpg", o.thumbnailUrl)
        assertEquals("https://www.youtube.com/embed/abc?feature=oembed", OEmbed.singleIframeSrc(o.html))
    }

    @Test fun parsesPhotoAndDropsHtml() {
        val o = OEmbed.parse("""{"type":"photo","url":"/p.jpg","html":"<b>x</b>","width":10,"height":10}""", "https://ex.com/oembed")!!
        assertEquals("https://ex.com/p.jpg", o.photoUrl)
        assertNull(o.html)
    }

    @Test fun rejectsInvalid() {
        assertNull(OEmbed.parse("not json", ep))
        assertNull(OEmbed.parse("""{"title":"no type"}""", ep))
        assertNull(OEmbed.parse("""{"type":"evil"}""", ep))
        assertNull(OEmbed.parse("""{"type":"video","version":"2.0"}""", ep))
        assertNull(OEmbed.parse("[1,2]", ep))
    }

    @Test fun unsafeThumbnailDropped() {
        val o = OEmbed.parse("""{"type":"rich","thumbnail_url":"javascript:alert(1)","html":"<div>x</div>"}""", ep)!!
        assertNull(o.thumbnailUrl)
        assertEquals("<div>x</div>", o.html)
    }

    @Test fun oversizedHtmlDropped() {
        val big = "x".repeat(OEmbed.MAX_HTML + 1)
        assertNull(OEmbed.parse("""{"type":"rich","html":"$big"}""", ep)!!.html)
    }

    @Test fun singleIframeOnlyForPlainHttpsIframe() {
        assertNull(OEmbed.singleIframeSrc("""<blockquote>t</blockquote><script src="https://platform.twitter.com/widgets.js"></script>"""))
        assertNull(OEmbed.singleIframeSrc("""<iframe src="http://insecure.example/embed"></iframe>"""))
        assertNull(OEmbed.singleIframeSrc("""<iframe src="javascript:alert(1)"></iframe>"""))
        assertNull(OEmbed.singleIframeSrc("""<iframe src="https://a.example/"></iframe><iframe src="https://b.example/"></iframe>"""))
        assertEquals("https://player.vimeo.com/video/1?a=1&b=2", OEmbed.singleIframeSrc("""<iframe src='https://player.vimeo.com/video/1?a=1&amp;b=2' allow="autoplay"></iframe>"""))
    }

    @Test fun knownEndpoints() {
        assertTrue(OEmbed.knownEndpoint("https://www.youtube.com/watch?v=abc")!!.startsWith("https://www.youtube.com/oembed?format=json&url=https%3A%2F%2Fwww.youtube.com"))
        assertTrue(OEmbed.knownEndpoint("https://youtu.be/abc")!!.startsWith("https://www.youtube.com/oembed"))
        assertTrue(OEmbed.knownEndpoint("https://vimeo.com/1")!!.startsWith("https://vimeo.com/api/oembed.json"))
        assertNull(OEmbed.knownEndpoint("https://notyoutube.com/x"))
        assertNull(OEmbed.knownEndpoint("https://example.com/"))
    }
}
