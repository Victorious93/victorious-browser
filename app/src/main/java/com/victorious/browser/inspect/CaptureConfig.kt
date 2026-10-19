package com.victorious.browser.inspect

/**
 * Configurable capture limits for the localhost inspection proxy.
 *
 * Body aggregation is capped so history / intercept never unbounded-buffers.
 * Defaults match the previous hard-coded 256 KiB; operators can raise or lower
 * via [setMaxBodyBytes] (clamped).
 */
object CaptureConfig {
    const val DEFAULT_MAX_BODY_BYTES = 256 * 1024
    const val MIN_MAX_BODY_BYTES = 4 * 1024
    const val MAX_MAX_BODY_BYTES = 2 * 1024 * 1024

    @Volatile
    private var maxBodyBytes: Int = DEFAULT_MAX_BODY_BYTES

    fun maxBodyBytes(): Int = maxBodyBytes

    /** Clamp [bytes] into [[MIN_MAX_BODY_BYTES], [MAX_MAX_BODY_BYTES]] and apply. */
    @Synchronized
    fun setMaxBodyBytes(bytes: Int) {
        maxBodyBytes = bytes.coerceIn(MIN_MAX_BODY_BYTES, MAX_MAX_BODY_BYTES)
    }

    @Synchronized
    fun resetForTests() {
        maxBodyBytes = DEFAULT_MAX_BODY_BYTES
    }
}
