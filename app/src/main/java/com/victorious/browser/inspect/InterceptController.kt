package com.victorious.browser.inspect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Burp-lite intercept gate for in-flight requests on the localhost proxy.
 *
 * When [enabled], [CaptureFiltersSource] parks each non-CONNECT request until the
 * UI calls [forward] (optionally with edits) or [drop], or until [timeoutMs]
 * elapses (auto-forward original).
 *
 * Replay traffic tagged with [REPLAY_MARKER_HEADER] is never parked.
 */
object InterceptController {
    const val REPLAY_MARKER_HEADER = "X-Victorious-Replay"
    const val DEFAULT_TIMEOUT_MS = 120_000L

    enum class Decision { FORWARD, DROP }

    data class InterceptResult(
        val decision: Decision,
        /** null → forward the original request unchanged. */
        val edited: EditableRequest? = null,
    )

    data class PendingIntercept(
        val exchangeId: Long,
        val original: EditableRequest,
        val enqueuedAt: Long = System.currentTimeMillis(),
    )

    private class Slot(
        val pending: PendingIntercept,
        val latch: CountDownLatch = CountDownLatch(1),
        @Volatile var result: InterceptResult = InterceptResult(Decision.FORWARD, null),
    )

    private val interceptOn = AtomicBoolean(false)
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val lock = Any()
    private val queue = ArrayDeque<Slot>()
    private val _pending = MutableStateFlow<PendingIntercept?>(null)
    val pending: StateFlow<PendingIntercept?> = _pending.asStateFlow()

    fun setEnabled(on: Boolean) {
        interceptOn.set(on)
        _enabled.value = on
        if (!on) {
            // Auto-forward everything still parked so WebView traffic unblocks.
            drainAll(InterceptResult(Decision.FORWARD, null))
        }
    }

    fun isEnabled(): Boolean = interceptOn.get()

    /**
     * Called from the proxy filter thread. Blocks until a decision or timeout.
     * Returns immediately with FORWARD when intercept is off.
     */
    fun awaitDecision(
        exchangeId: Long,
        original: EditableRequest,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): InterceptResult {
        if (!interceptOn.get()) {
            return InterceptResult(Decision.FORWARD, null)
        }
        if (RequestEditMerge.isConnect(original.method)) {
            return InterceptResult(Decision.FORWARD, null)
        }
        if (original.headers.any { it.name.equals(REPLAY_MARKER_HEADER, ignoreCase = true) }) {
            return InterceptResult(Decision.FORWARD, null)
        }

        val slot = Slot(PendingIntercept(exchangeId, original))
        synchronized(lock) {
            queue.addLast(slot)
            publishHead()
        }
        val released = try {
            slot.latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        synchronized(lock) {
            queue.remove(slot)
            publishHead()
        }
        return if (released) slot.result else InterceptResult(Decision.FORWARD, null)
    }

    fun forward(edited: EditableRequest? = null) {
        decide(InterceptResult(Decision.FORWARD, edited))
    }

    fun drop() {
        decide(InterceptResult(Decision.DROP, null))
    }

    private fun decide(result: InterceptResult) {
        val slot = synchronized(lock) {
            queue.firstOrNull()
        } ?: return
        slot.result = result
        slot.latch.countDown()
    }

    private fun drainAll(result: InterceptResult) {
        val slots = synchronized(lock) {
            val copy = queue.toList()
            queue.clear()
            publishHead()
            copy
        }
        for (s in slots) {
            s.result = result
            s.latch.countDown()
        }
    }

    private fun publishHead() {
        _pending.value = queue.firstOrNull()?.pending
    }

    /** Test / reset helper. */
    @Synchronized
    fun resetForTests() {
        drainAll(InterceptResult(Decision.DROP, null))
        interceptOn.set(false)
        _enabled.value = false
    }
}
