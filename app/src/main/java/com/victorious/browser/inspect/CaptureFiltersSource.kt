package com.victorious.browser.inspect

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.HttpObject
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import org.littleshoot.proxy.HttpFilters
import org.littleshoot.proxy.HttpFiltersAdapter
import org.littleshoot.proxy.HttpFiltersSourceAdapter

/**
 * Records a [CapturedExchange] for every request/response pair flowing through the
 * local proxy into [CaptureStore], and (when [InterceptController] is enabled)
 * parks non-CONNECT requests for operator edit / forward / drop.
 *
 * Request and response aggregation is capped by [CaptureConfig.maxBodyBytes] so
 * bodies are available for intercept edit, history detail, and search without
 * unbounded buffering.
 */
class CaptureFiltersSource : HttpFiltersSourceAdapter() {

    companion object {
        /** @deprecated Use [CaptureConfig.maxBodyBytes]; retained for callers. */
        const val MAX_REQUEST_BUFFER_BYTES = CaptureConfig.DEFAULT_MAX_BODY_BYTES
        const val MAX_RESPONSE_BUFFER_BYTES = CaptureConfig.DEFAULT_MAX_BODY_BYTES
    }

    override fun getMaximumRequestBufferSizeInBytes(): Int = CaptureConfig.maxBodyBytes()

    override fun getMaximumResponseBufferSizeInBytes(): Int = CaptureConfig.maxBodyBytes()

    override fun filterRequest(
        originalRequest: HttpRequest,
        ctx: ChannelHandlerContext,
    ): HttpFilters {
        val id = CaptureStore.nextId()
        return object : HttpFiltersAdapter(originalRequest, ctx) {
            private var recorded = false

            override fun clientToProxyRequest(httpObject: HttpObject): HttpResponse? {
                if (recorded || httpObject !is HttpRequest) return null
                recorded = true

                val exchange = toExchange(id, httpObject)
                CaptureStore.record(exchange)

                // CONNECT establishes the tunnel; never park it for edit.
                if (RequestEditMerge.isConnect(httpObject.method().name())) {
                    return null
                }

                val isReplay = httpObject.headers().contains(InterceptController.REPLAY_MARKER_HEADER)
                // Never leak the internal replay marker to upstream servers.
                httpObject.headers().remove(InterceptController.REPLAY_MARKER_HEADER)

                if (isReplay) {
                    return null
                }

                val editable = NettyRequestApplier.toEditable(httpObject, exchange.url)
                val decision = InterceptController.awaitDecision(id, editable)
                return when (decision.decision) {
                    InterceptController.Decision.DROP -> NettyRequestApplier.dropResponse()
                    InterceptController.Decision.FORWARD -> {
                        val edited = decision.edited
                        if (edited != null) {
                            NettyRequestApplier.apply(httpObject, edited)
                            replaceRecordedRequest(id, edited)
                        }
                        null
                    }
                }
            }

            override fun serverToProxyResponse(httpObject: HttpObject): HttpObject {
                if (httpObject is HttpResponse) {
                    val headers = httpObject.headers().entries().map {
                        CapturedHeader(it.key, it.value)
                    }
                    val (body, truncated, wireBytes) = extractBody(httpObject)
                    CaptureStore.update(id) { ex ->
                        ex.statusCode = httpObject.status().code()
                        ex.responseHeaders = headers
                        ex.responseReceivedAt = System.currentTimeMillis()
                        if (body != null) {
                            ex.responseBody = body
                            ex.responseBodyTruncated = truncated
                            ex.responseBytes = wireBytes
                        } else {
                            ex.responseBytes = headers.firstOrNull {
                                it.name.equals("Content-Length", true)
                            }?.value?.toLongOrNull() ?: ex.responseBytes
                        }
                    }
                }
                return httpObject
            }
        }
    }

    /**
     * [CapturedExchange] keeps method/url/headers as vals; swap the list entry
     * when intercept edits change the outbound request.
     */
    private fun replaceRecordedRequest(id: Long, edited: EditableRequest) {
        val host = RequestEditMerge.hostPortFromUrl(edited.url)?.substringBefore(':') ?: ""
        val scheme = when {
            edited.url.startsWith("https", true) -> "https"
            edited.url.startsWith("http", true) -> "http"
            else -> "http"
        }
        val list = CaptureStore.exchanges.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = list[idx]
        val cap = CaptureConfig.maxBodyBytes()
        val rawBody = edited.body?.copyOf()
        val (body, truncated) = when {
            rawBody == null -> null to false
            rawBody.size > cap -> rawBody.copyOf(cap) to true
            else -> rawBody to false
        }
        val replacement = CapturedExchange(
            id = old.id,
            method = edited.method,
            url = edited.url,
            host = host.ifEmpty { old.host },
            scheme = scheme,
            requestHeaders = edited.headers.filterNot {
                it.name.equals(InterceptController.REPLAY_MARKER_HEADER, ignoreCase = true)
            },
            requestStartedAt = old.requestStartedAt,
            statusCode = old.statusCode,
            responseHeaders = old.responseHeaders,
            responseReceivedAt = old.responseReceivedAt,
            requestBytes = edited.body?.size?.toLong() ?: old.requestBytes,
            responseBytes = old.responseBytes,
            requestBody = body,
            responseBody = old.responseBody,
            requestBodyTruncated = truncated,
            responseBodyTruncated = old.responseBodyTruncated,
        )
        val updated = list.toMutableList()
        updated[idx] = replacement
        CaptureStore.replaceAll(updated)
    }

    private fun toExchange(id: Long, req: HttpRequest): CapturedExchange {
        val headers = req.headers().entries().map { CapturedHeader(it.key, it.value) }
            .filterNot { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, ignoreCase = true) }
        val hostHeader = headers.firstOrNull { it.name.equals("Host", true) }?.value.orEmpty()
        val uri = req.uri()
        val scheme = if (uri.startsWith("https", true) || req.method().name() == "CONNECT") "https" else "http"
        val host = hostHeader.ifEmpty { uri.substringAfter("://", "").substringBefore('/') }
        val url = when {
            uri.contains("://") -> uri
            host.isNotEmpty() -> "$scheme://$host$uri"
            else -> uri
        }
        val (body, truncated, wireBytes) = extractBody(req)
        val reqBytes = wireBytes.takeIf { it > 0 }
            ?: headers.firstOrNull { it.name.equals("Content-Length", true) }?.value?.toLongOrNull()
            ?: 0
        return CapturedExchange(
            id = id,
            method = req.method().name(),
            url = url,
            host = host.substringBefore(':'),
            scheme = scheme,
            requestHeaders = headers,
            requestStartedAt = System.currentTimeMillis(),
            requestBytes = reqBytes,
            requestBody = body,
            requestBodyTruncated = truncated,
        )
    }

    /**
     * Copy aggregated body bytes from a FullHttpRequest / FullHttpResponse,
     * truncating to [CaptureConfig.maxBodyBytes] when needed.
     *
     * @return Triple(capturedBytesOrNull, truncated, wireByteCount)
     */
    private fun extractBody(msg: Any): Triple<ByteArray?, Boolean, Long> {
        val buf: ByteBuf? = when (msg) {
            is FullHttpRequest -> msg.content()
            is FullHttpResponse -> msg.content()
            else -> null
        }
        if (buf == null || buf.readableBytes() <= 0) {
            return Triple(null, false, 0L)
        }
        val wire = buf.readableBytes()
        val cap = CaptureConfig.maxBodyBytes()
        val take = minOf(wire, cap)
        val bytes = ByteArray(take)
        buf.getBytes(buf.readerIndex(), bytes)
        return Triple(bytes, wire > cap, wire.toLong())
    }
}
