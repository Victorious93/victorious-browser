package com.victorious.browser.inspect

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * Capture history store.
 *
 * Implementation choice: a simple in-memory store exposed as a [StateFlow] for the
 * Compose UI, with a cap to avoid unbounded growth. This is intentionally NOT Room —
 * the captured model ([CapturedExchange]) is a plain data class, so swapping in a Room
 * DAO later is a mechanical change. Chosen for zero schema/KSP overhead in this layer;
 * noted here as the deliberate "simple local store" option from the task.
 */
object CaptureStore {
    private const val MAX_ENTRIES = 1000

    private val idGen = AtomicLong(0)
    private val _exchanges = MutableStateFlow<List<CapturedExchange>>(emptyList())
    val exchanges: StateFlow<List<CapturedExchange>> = _exchanges.asStateFlow()

    fun nextId(): Long = idGen.incrementAndGet()

    @Synchronized
    fun record(exchange: CapturedExchange) {
        val updated = (_exchanges.value + exchange).takeLast(MAX_ENTRIES)
        _exchanges.value = updated
    }

    @Synchronized
    fun update(id: Long, mutate: (CapturedExchange) -> Unit) {
        val list = _exchanges.value
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return
        mutate(list[idx])
        // Re-emit a new list instance so StateFlow collectors recompose.
        _exchanges.value = list.toList()
    }

    /**
     * Replace the entire history list (used when intercept edits change val fields
     * on a recorded exchange). Still capped at [MAX_ENTRIES].
     */
    @Synchronized
    fun replaceAll(exchanges: List<CapturedExchange>) {
        _exchanges.value = exchanges.takeLast(MAX_ENTRIES)
    }

    @Synchronized
    fun clear() {
        _exchanges.value = emptyList()
    }
}
