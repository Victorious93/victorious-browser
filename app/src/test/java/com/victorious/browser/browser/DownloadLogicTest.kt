package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadLogicTest {
    @Test fun fileNameFromUrlPath() {
        assertEquals("report.pdf", DownloadLogic.guessFileName("https://x.com/a/report.pdf?x=1", null, null))
        assertEquals("my file.txt", DownloadLogic.guessFileName("https://x.com/my%20file.txt", null, null))
    }

    @Test fun contentDispositionWins() {
        assertEquals("q.csv", DownloadLogic.guessFileName("https://x.com/dl", "attachment; filename=\"q.csv\"", null))
        assertEquals("plain.bin", DownloadLogic.guessFileName("https://x.com/dl", "attachment; filename=plain.bin", null))
        assertEquals("résumé.pdf", DownloadLogic.guessFileName("https://x.com/dl", "attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf", null))
    }

    @Test fun stripsTraversalAndAddsExtension() {
        val n = DownloadLogic.guessFileName("https://x.com/dl", "attachment; filename=\"../../etc/passwd\"", null)
        assertFalse(n.contains('/'))
        assertFalse(n.startsWith("."))
        assertEquals("download.pdf", DownloadLogic.guessFileName("https://x.com/", null, "application/pdf"))
        assertEquals("getfile.zip", DownloadLogic.guessFileName("https://x.com/getfile", null, "application/zip; charset=x"))
        assertEquals("download", DownloadLogic.guessFileName("not a url", null, null))
    }

    @Test fun codecAndAdd() {
        val r1 = DownloadRecord(1, "https://a/1", "a.bin", "", 10)
        val r2 = DownloadRecord(2, "https://a/2", "b.bin", "x/y", 11)
        val list = DownloadLogic.add(DownloadLogic.add(emptyList(), r1), r2)
        assertEquals(listOf(r2, r1), list)
        assertEquals(list, DownloadLogic.decode(DownloadLogic.encode(list)))
        assertTrue(DownloadLogic.decode("nope").isEmpty())
        assertEquals(1, DownloadLogic.add(list, r1, max = 1).size)
    }

    @Test fun formatsBytes() {
        assertEquals("512 B", DownloadLogic.formatBytes(512))
        assertEquals("1.5 KB", DownloadLogic.formatBytes(1536))
        assertEquals("?", DownloadLogic.formatBytes(-1))
    }
}
