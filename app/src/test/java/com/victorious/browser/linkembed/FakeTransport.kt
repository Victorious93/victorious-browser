package com.victorious.browser.linkembed

import java.io.ByteArrayInputStream
import java.io.IOException

/** Scripted transport for tests: url → response; records every request. */
class FakeTransport(private val routes: Map<String, () -> TransportResponse>) : HttpTransport {
    val requests = mutableListOf<Pair<String, Map<String, String>>>()
    override fun execute(url: String, headers: Map<String, String>): TransportResponse {
        requests += url to headers
        val r = routes[url] ?: throw IOException("no route for $url")
        return r()
    }

    companion object {
        fun ok(body: String, contentType: String = "text/html; charset=utf-8", headers: Map<String, String> = emptyMap()) =
            { bytes(body.toByteArray(), contentType, headers) }

        fun bytes(body: ByteArray, contentType: String, headers: Map<String, String> = emptyMap(), status: Int = 200) =
            TransportResponse(
                status,
                (headers + ("Content-Type" to contentType)).mapValues { listOf(it.value) },
                ByteArrayInputStream(body),
            )

        fun redirect(to: String, status: Int = 302) = {
            TransportResponse(status, mapOf("Location" to listOf(to)), ByteArrayInputStream(ByteArray(0)))
        }
    }
}
