package com.victorious.browser.inspect

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
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
 * Request aggregation is capped so [FullHttpRequest] bodies are available for
 * intercept edit and history replay without unbounded buffering.
 */
class CaptureFiltersSource(
    private val blockerSettings: ContentBlockerSettings,
) : HttpFiltersSourceAdapter() {

    companion object {
        /** Cap for aggregated request bodies (intercept + capture). */
        const val MAX_REQUEST_BUFFER_BYTES = 256 * 1024
        const val MAX_RESPONSE_BUFFER_BYTES = 256 * 1024

        /** Synthetic status recorded for a blocked exchange; no real server was contacted. */
        const val BLOCKED_STATUS_CODE = -1
    }

    override fun getMaximumRequestBufferSizeInBytes(): Int = MAX_REQUEST_BUFFER_BYTES

    override fun getMaximumResponseBufferSizeInBytes(): Int = MAX_RESPONSE_BUFFER_BYTES

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

                var exchange = toExchange(id, httpObject)

                // Content blocker: match before recording so a blocked request is
                // logged as blocked, never silently dropped from the capture history.
                val blockedBy = ContentBlocker.isBlocked(
                    exchange.host.substringBefore(':'),
                    blockerSettings.activeCategories(),
                )
                if (blockedBy != null) {
                    exchange = exchange.copy(blocked = blockedBy, statusCode = BLOCKED_STATUS_CODE)
                }
                CaptureStore.record(exchange)

                if (blockedBy != null) {
                    return NettyRequestApplier.blockedResponse(blockedBy)
                }

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
            requestBody = edited.body?.copyOf(),
            responseBody = old.responseBody,
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
        val body: ByteArray? = if (req is FullHttpRequest) {
            val buf = req.content()
            if (buf != null && buf.readableBytes() > 0) {
                val bytes = ByteArray(buf.readableBytes())
                buf.getBytes(buf.readerIndex(), bytes)
                bytes
            } else {
                null
            }
        } else {
            null
        }
        val reqBytes = body?.size?.toLong()
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
        )
    }
}
