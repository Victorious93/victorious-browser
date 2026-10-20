package com.victorious.browser.bridge

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * On-device confirmation gate for [BridgeV1.OP_CONFIG_IMPORT].
 * Import never applies until the user explicitly approves a pending request
 * from the Fleet control screen. Pure coordination — no secrets stored.
 */
object ConfigImportGate {
    data class Pending(
        val requestId: String,
        val conflictPolicy: ConflictPolicy,
        val payloadSummary: String,
        val createdAtMs: Long = System.currentTimeMillis(),
    )

    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()

    /** requestIds the user has approved (consumed once by the next matching import). */
    private val approvedIds = mutableSetOf<String>()
    private val lock = Any()

    fun submitPending(requestId: String, conflictPolicy: ConflictPolicy, summary: String) {
        _pending.value = Pending(requestId, conflictPolicy, summary)
    }

    fun approve(requestId: String) {
        synchronized(lock) {
            approvedIds.add(requestId)
            if (_pending.value?.requestId == requestId) _pending.value = null
        }
    }

    fun reject(requestId: String) {
        synchronized(lock) {
            approvedIds.remove(requestId)
            if (_pending.value?.requestId == requestId) _pending.value = null
        }
    }

    fun consumeApproval(requestId: String): Boolean {
        synchronized(lock) {
            return approvedIds.remove(requestId)
        }
    }

    fun isApproved(requestId: String): Boolean = synchronized(lock) { requestId in approvedIds }

    /** Test helper. */
    fun reset() {
        synchronized(lock) {
            approvedIds.clear()
            _pending.value = null
        }
    }

    fun summarizePayload(params: JSONObject): String {
        val prefs = params.optJSONArray("preferences")
        val count = prefs?.length() ?: 0
        return "preferences entries=$count"
    }
}
