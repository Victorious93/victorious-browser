package com.victorious.browser.inspect

/**
 * Mutable-friendly snapshot of an HTTP request for intercept edit / replay.
 *
 * Pure data — no Netty or Android types — so unit tests can construct and merge
 * without the proxy runtime.
 */
data class EditableRequest(
    val method: String,
    val url: String,
    val headers: List<CapturedHeader>,
    val body: ByteArray? = null,
) {
    fun bodyAsUtf8(): String = body?.toString(Charsets.UTF_8) ?: ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EditableRequest) return false
        return method == other.method &&
            url == other.url &&
            headers == other.headers &&
            body.contentEquals(other.body)
    }

    override fun hashCode(): Int {
        var result = method.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        return result
    }
}
