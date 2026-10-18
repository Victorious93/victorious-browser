package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class HistoryFilterTest {

    private fun ex(
        id: Long = 1,
        method: String = "GET",
        url: String = "https://api.example.com/v1/users",
        host: String = "api.example.com",
        status: Int? = 200,
        reqHeaders: List<CapturedHeader> = emptyList(),
        resHeaders: List<CapturedHeader> = listOf(
            CapturedHeader("Content-Type", "application/json"),
        ),
        reqBody: ByteArray? = null,
        resBody: ByteArray? = "{\"ok\":true}".toByteArray(),
    ): CapturedExchange = CapturedExchange(
        id = id,
        method = method,
        url = url,
        host = host,
        scheme = "https",
        requestHeaders = reqHeaders,
        requestStartedAt = 1L,
        statusCode = status,
        responseHeaders = resHeaders,
        responseReceivedAt = 2L,
        requestBody = reqBody,
        responseBody = resBody,
    )

    private fun enc(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

    @Test
    fun emptyCriteria_returnsAll() {
        val list = listOf(ex(1), ex(2, method = "POST"))
        assertEquals(2, HistoryFilter.apply(list, HistoryFilter.Criteria()).size)
    }

    @Test
    fun filterByMethod() {
        val list = listOf(ex(1, method = "GET"), ex(2, method = "POST"), ex(3, method = "GET"))
        val out = HistoryFilter.apply(list, HistoryFilter.Criteria(method = "post"))
        assertEquals(1, out.size)
        assertEquals(2L, out[0].id)
    }

    @Test
    fun filterByStatusPrefix() {
        val list = listOf(
            ex(1, status = 200),
            ex(2, status = 404),
            ex(3, status = 500),
        )
        val out = HistoryFilter.apply(list, HistoryFilter.Criteria(status = "4"))
        assertEquals(1, out.size)
        assertEquals(404, out[0].statusCode)
    }

    @Test
    fun filterByHostContains() {
        val list = listOf(
            ex(1, host = "api.example.com"),
            ex(2, host = "cdn.other.test", url = "https://cdn.other.test/x"),
        )
        val out = HistoryFilter.apply(list, HistoryFilter.Criteria(host = "other"))
        assertEquals(1, out.size)
        assertEquals(2L, out[0].id)
    }

    @Test
    fun filterByContentType() {
        val list = listOf(
            ex(1, resHeaders = listOf(CapturedHeader("Content-Type", "application/json"))),
            ex(
                2,
                resHeaders = listOf(CapturedHeader("Content-Type", "image/png")),
                resBody = byteArrayOf(0x89.toByte(), 0x50),
            ),
        )
        val out = HistoryFilter.apply(list, HistoryFilter.Criteria(contentType = "image"))
        assertEquals(1, out.size)
        assertEquals(2L, out[0].id)
    }

    @Test
    fun searchMatchesUrlAndBody() {
        val list = listOf(
            ex(1, url = "https://api.example.com/login"),
            ex(2, url = "https://api.example.com/health", resBody = "{\"role\":\"admin\"}".toByteArray()),
        )
        assertEquals(1, HistoryFilter.apply(list, HistoryFilter.Criteria(query = "login")).size)
        assertEquals(1, HistoryFilter.apply(list, HistoryFilter.Criteria(query = "admin")).size)
    }

    @Test
    fun searchMatchesHeaderAndCookie() {
        val list = listOf(
            ex(
                1,
                reqHeaders = listOf(
                    CapturedHeader("X-Trace", "abc-trace-id"),
                    CapturedHeader("Cookie", "session=secretvalue"),
                ),
            ),
            ex(2),
        )
        assertTrue(HistoryFilter.matchesQuery(list[0], "trace-id"))
        assertTrue(HistoryFilter.matchesQuery(list[0], "secretvalue"))
        assertFalse(HistoryFilter.matchesQuery(list[1], "secretvalue"))
    }

    @Test
    fun searchMatchesJwtClaims() {
        val header = enc("{\"alg\":\"HS256\",\"typ\":\"JWT\"}")
        val payload = enc("{\"sub\":\"fleet-operator\",\"aud\":\"victorious\"}")
        val token = "$header.$payload.sig"
        val list = listOf(
            ex(
                1,
                reqHeaders = listOf(CapturedHeader("Authorization", "Bearer $token")),
                resBody = null,
            ),
        )
        assertTrue(HistoryFilter.matchesQuery(list[0], "fleet-operator"))
        assertTrue(HistoryFilter.matchesQuery(list[0], "aud"))
        assertFalse(HistoryFilter.matchesQuery(list[0], "nope-claim"))
    }

    @Test
    fun combinedFiltersAndQuery() {
        val list = listOf(
            ex(1, method = "POST", host = "api.example.com", status = 201, resBody = "{\"ok\":true}".toByteArray()),
            ex(2, method = "POST", host = "api.example.com", status = 400, resBody = "{\"error\":\"bad\"}".toByteArray()),
            ex(3, method = "GET", host = "api.example.com", status = 200),
        )
        val out = HistoryFilter.apply(
            list,
            HistoryFilter.Criteria(method = "POST", status = "201", query = "ok", host = "example"),
        )
        assertEquals(1, out.size)
        assertEquals(1L, out[0].id)
    }
}
