package com.victorious.browser.urltools

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkShortenerTest {
    @Test
    fun buildsEncodedRequestUrl() {
        assertEquals(
            "https://tinyurl.com/api-create.php?url=https%3A%2F%2Fexample.com%2Fa+b",
            LinkShortener.buildRequestUrl("https://example.com/a b"),
        )
    }
}
