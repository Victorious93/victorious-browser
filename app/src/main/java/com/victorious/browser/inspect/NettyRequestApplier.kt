package com.victorious.browser.inspect

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpRequest
import io.netty.handler.codec.http.HttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpVersion

/**
 * Applies [EditableRequest] edits onto a live Netty [HttpRequest] inside the
 * LittleProxy filter chain, and builds synthetic drop responses.
 */
object NettyRequestApplier {

    fun toEditable(req: HttpRequest, absoluteUrl: String): EditableRequest {
        val headers = req.headers().entries().map { CapturedHeader(it.key, it.value) }
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
        return EditableRequest(
            method = req.method().name(),
            url = absoluteUrl,
            headers = RequestEditMerge.stripHopByHop(headers)
                .filterNot { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, ignoreCase = true) },
            body = body,
        )
    }

    fun apply(req: HttpRequest, edited: EditableRequest) {
        val originalAbsolute = req.uri().contains("://")
        req.method = HttpMethod.valueOf(edited.method.uppercase())
        req.uri = RequestEditMerge.requestUriForProxy(edited.url, preferAbsolute = originalAbsolute)

        req.headers().clear()
        val headers = RequestEditMerge.ensureHostHeader(
            RequestEditMerge.stripHopByHop(edited.headers)
                .filterNot { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, ignoreCase = true) },
            edited.url,
        ).let { RequestEditMerge.syncContentLength(it, edited.body) }

        for (h in headers) {
            req.headers().add(h.name, h.value)
        }

        if (req is FullHttpRequest) {
            val content = req.content()
            content.clear()
            val body = edited.body
            if (body != null && body.isNotEmpty()) {
                content.writeBytes(body)
            }
            req.headers().set(HttpHeaderNames.CONTENT_LENGTH, body?.size ?: 0)
        }
    }

    /** Synthetic response when the content blocker matches a request's host. */
    fun blockedResponse(category: BlockCategory): HttpResponse {
        val body = Unpooled.copiedBuffer(
            "Blocked by Victorious Browser content blocker (${category.label})",
            Charsets.UTF_8,
        )
        val resp = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN, body)
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8")
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())
        resp.headers().set(HttpHeaderNames.CONNECTION, "close")
        return resp
    }

    /** Synthetic response when the operator drops an intercepted request. */
    fun dropResponse(): HttpResponse {
        val body = Unpooled.copiedBuffer("Request dropped by Victorious Browser intercept", Charsets.UTF_8)
        val resp = DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.FORBIDDEN,
            body,
        )
        resp.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=utf-8")
        resp.headers().set(HttpHeaderNames.CONTENT_LENGTH, body.readableBytes())
        resp.headers().set(HttpHeaderNames.CONNECTION, "close")
        return resp
    }
}
