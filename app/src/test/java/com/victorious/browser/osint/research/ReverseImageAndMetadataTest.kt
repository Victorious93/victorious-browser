package com.victorious.browser.osint.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReverseImageAndMetadataTest {
    private val img = "https://cdn.example.com/a b.jpg?x=1&y=2"

    @Test fun byUrlLinks() {
        val clean = "https://cdn.example.com/a.jpg?x=1&y=2"
        val e = "https%3A%2F%2Fcdn.example.com%2Fa.jpg%3Fx%3D1%26y%3D2"
        assertEquals("https://lens.google.com/uploadbyurl?url=$e", ReverseImage.byUrl(ReverseImage.Engine.GOOGLE_LENS, clean))
        assertEquals("https://www.bing.com/images/search?view=detailv2&iss=sbi&q=imgurl:$e", ReverseImage.byUrl(ReverseImage.Engine.BING, clean))
        assertEquals("https://yandex.com/images/search?rpt=imageview&url=$e", ReverseImage.byUrl(ReverseImage.Engine.YANDEX, clean))
        assertEquals("https://tineye.com/search?url=$e", ReverseImage.byUrl(ReverseImage.Engine.TINEYE, clean))
        assertEquals(4, ReverseImage.allByUrl(clean).size)
        assertEquals(5, ReverseImage.allByUrl(clean, "https://x.example/s?u={url}").size)
    }

    @Test fun rejectsNonHttpImages() {
        assertNull(ReverseImage.byUrl(ReverseImage.Engine.TINEYE, "data:image/png;base64,AAAA"))
        assertNull(ReverseImage.byUrl(ReverseImage.Engine.TINEYE, "content://media/1"))
        assertNull(ReverseImage.byUrl(ReverseImage.Engine.TINEYE, img)) // contains whitespace
        assertFalse(ReverseImage.isShareableImageUrl("https://"))
        assertFalse(ReverseImage.isValidCustomTemplate("https://x.example/"))
        assertNull(ReverseImage.custom("javascript:{url}", "https://a.example/i.png"))
        ReverseImage.Engine.entries.forEach { assertTrue(ReverseImage.uploadPage(it).startsWith("https://")) }
    }

    @Test fun hashes() {
        val b = "abc".toByteArray()
        assertEquals("900150983cd24fb0d6963f7d28e17f72", ImageMetadata.md5(b))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ImageMetadata.sha256(b))
    }

    @Test fun perceptualHashes() {
        // Left-to-right decreasing brightness → every bit set.
        val dec = IntArray(72) { i -> 255 - (i % 9) * 20 }
        assertEquals("ffffffffffffffff", ImageMetadata.dHash(dec))
        val inc = IntArray(72) { i -> (i % 9) * 20 }
        assertEquals("0000000000000000", ImageMetadata.dHash(inc))
        assertEquals(64, ImageMetadata.hamming(ImageMetadata.dHash(dec), ImageMetadata.dHash(inc)))
        val half = IntArray(64) { if (it < 32) 200 else 10 }
        assertEquals("ffffffff00000000", ImageMetadata.aHash(half))
        assertEquals(0, ImageMetadata.hamming("00ff", "00ff"))
        assertEquals(255, ImageMetadata.luma(0xFFFFFFFF.toInt()))
        assertEquals(0, ImageMetadata.luma(0xFF000000.toInt()))
    }

    @Test fun gps() {
        val lat = ImageMetadata.dmsToDecimal("40/1,26/1,4630/100", "N")!!
        assertEquals(40.446194, lat, 1e-6)
        assertEquals(-79.9822, ImageMetadata.dmsToDecimal("79/1,58/1,5592/100", "W")!!, 1e-4)
        assertNull(ImageMetadata.dmsToDecimal("1/0,0/1,0/1", "N"))
        assertNull(ImageMetadata.dmsToDecimal("garbage", "N"))
        val exif = mapOf("GPSLatitude" to "40/1,26/1,4630/100", "GPSLatitudeRef" to "N", "GPSLongitude" to "79/1,58/1,5592/100", "GPSLongitudeRef" to "W")
        val (la, lo) = ImageMetadata.gpsFrom(exif)!!
        assertTrue(la > 40 && lo < -79)
        assertNull(ImageMetadata.gpsFrom(mapOf("GPSLatitude" to "0/1,0/1,0/1", "GPSLongitude" to "0/1,0/1,0/1")))
        assertTrue(ImageMetadata.mapsUrl(1.5, -2.25).startsWith("https://www.openstreetmap.org/?mlat=1.500000&mlon=-2.250000"))
    }

    @Test fun exifCleanAndSummary() {
        val c = ImageMetadata.cleanExif(mapOf("Model" to " Pixel 8 ", "Make" to "Google", "Junk" to "x", "Artist" to "", "FNumber" to "0/0"))
        assertEquals(listOf("Make", "Model"), c.keys.toList())
        assertEquals("Pixel 8", c["Model"])
        val s = ImageMetadata.Summary(4000, 3000, "image/jpeg", 2_500_000, "m", "s", "d", c, null, null)
        val t = s.toText("photo.jpg")
        assertTrue(t.contains("Dimensions: 4000×3000"))
        assertTrue(t.contains("Size: 2.38 MB"))
        assertTrue(t.contains("  Model: Pixel 8"))
        assertFalse(s.hasGps)
        assertEquals("512 B", ImageMetadata.humanSize(512))
    }

    @Test fun sniffMime() {
        assertEquals("image/jpeg", ImageMetadata.sniffMime(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0)))
        assertEquals("image/png", ImageMetadata.sniffMime(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10)))
        assertEquals("image/gif", ImageMetadata.sniffMime("GIF89a....".toByteArray()))
        assertEquals("image/webp", ImageMetadata.sniffMime("RIFF\u0000\u0000\u0000\u0000WEBP".toByteArray()))
        assertNull(ImageMetadata.sniffMime("hello".toByteArray()))
    }
}
