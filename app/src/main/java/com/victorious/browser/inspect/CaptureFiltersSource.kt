package com.victorious.browser.inspect

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter

/**
 * Records a [CapturedExchange] for every request/response pair flowing through the
 * local proxy into [CaptureStore]. Metadata + headers only in this layer; the model
 * leaves room for body capture later.
 */
class CaptureFiltersSource : HttpFiltersSourceAdapter() {

    override fun filterRequest(
        originalRequest: HttpRequest,
        ctx: ChannelHandlerContext,
    ): HttpFilters {
        val id = CaptureStore.nextId()
        return object : HttpFiltersAdapter(originalRequest, ctx) {
            private var recorded = false

            override fun clientToProxyRequest(httpObject: HttpObject): HttpResponse? {
                if (!recorded && httpObject is HttpRequest) {
                    recorded = true
                    CaptureStore.record(toExchange(id, httpObject))
                }
                return null
            }

            override fun serverToProxyResponse(httpObject: HttpObject): HttpObject {
                if (httpObject is HttpResponse) {
                    val headers = httpObject.headers().entries().map {
                        CapturedHeader(it.key, it.value)
                    }
                    CaptureStore.update(id) { ex ->
                        ex.statusCode = httpObject.status().code()
                        ex.responseHeaders = headers
                        ex.responseReceivedAt = System.currentTimeMillis()
                        ex.responseBytes = headers.firstOrNull {
                            it.name.equals("Content-Length", true)
                        }?.value?.toLongOrNull() ?: ex.responseBytes
                    }
                }
                return httpObject
            }
        }
    }

    private fun toExchange(id: Long, req: HttpRequest): CapturedExchange {
        val headers = req.headers().entries().map { CapturedHeader(it.key, it.value) }
        val hostHeader = headers.firstOrNull { it.name.equals("Host", true) }?.value.orEmpty()
        val uri = req.uri()
        // CONNECT and absolute-form URIs both appear here; normalize best-effort.
        val scheme = if (uri.startsWith("https", true) || req.method().name() == "CONNECT") "https" else "http"
        val host = hostHeader.ifEmpty { uri.substringAfter("://", "").substringBefore('/') }
        val url = when {
            uri.contains("://") -> uri
            host.isNotEmpty() -> "$scheme://$host$uri"
            else -> uri
        }
        val reqBytes = headers.firstOrNull {
            it.name.equals("Content-Length", true)
        }?.value?.toLongOrNull() ?: 0
        return CapturedExchange(
            id = id,
            method = req.method().name(),
            url = url,
            host = host.substringBefore(':'),
            scheme = scheme,
            requestHeaders = headers,
            requestStartedAt = System.currentTimeMillis(),
            requestBytes = reqBytes,
        )
    }
}
