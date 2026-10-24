package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the pure JSON codec behind [BookmarkStore] (the Context-bound store is not unit-tested). */
class BookmarkStoreTest {
    @Test
    fun roundTripsBookmarks() {
        val list = listOf(
            Bookmark("https://example.com/", "Example"),
            Bookmark("https://youtube.com/", "YouTube"),
        )
        assertEquals(list, decodeBookmarks(encodeBookmarks(list)))
    }

    @Test
    fun emptyAndNullDecodeToEmpty() {
        assertTrue(decodeBookmarks(null).isEmpty())
        assertTrue(decodeBookmarks("").isEmpty())
        assertTrue(decodeBookmarks("   ").isEmpty())
    }

    @Test
    fun malformedJsonDecodesToEmptyNotCrash() {
        assertTrue(decodeBookmarks("{not valid json").isEmpty())
        assertTrue(decodeBookmarks("[1, 2, 3]").isEmpty())
    }

    @Test
    fun entriesWithoutUrlAreSkipped() {
        // A stored object missing "url" is dropped rather than becoming a blank bookmark.
        val raw = """[{"title":"no url here"},{"url":"https://ok.test/","title":"ok"}]"""
        val decoded = decodeBookmarks(raw)
        assertEquals(1, decoded.size)
        assertEquals("https://ok.test/", decoded[0].url)
    }

    @Test
    fun titleDefaultsToUrlWhenAbsent() {
        val decoded = decodeBookmarks("""[{"url":"https://notitle.test/"}]""")
        assertEquals(1, decoded.size)
        assertEquals("https://notitle.test/", decoded[0].title)
    }
}
