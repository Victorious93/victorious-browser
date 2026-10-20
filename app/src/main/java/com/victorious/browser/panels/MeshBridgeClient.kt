package com.victorious.browser.panels

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * Best-effort, READ-ONLY bound-Messenger client for the Victorious Mesh bridge.
 * Calls only `mesh.status` and `mesh.ip.get` ([MeshBridgeProtocol.ALLOWED_OPS]).
 *
 * Degrades gracefully: missing app, bind refusal, SecurityException,
 * `unauthorized` (this browser is not on Mesh's allowlist until Fleet
 * Orchestrator sign-off lands there), `external_control_disabled`, or timeout
 * all return [BridgeReply.Failure] — never throw. Callers fall back to passive
 * NetworkCapabilities inference.
 *
 * Never logs responses (mesh.ip is SENSITIVE).
 */
class MeshBridgeClient(context: Context, private val timeoutMs: Long = MeshBridgeProtocol.DEFAULT_TIMEOUT_MS) {
    private val app = context.applicationContext

    @Volatile private var nodeId: String? = null

    suspend fun status(): BridgeReply = call(MeshBridgeProtocol.OP_MESH_STATUS)

    /** SENSITIVE result — keep in memory only. */
    suspend fun meshIp(): BridgeReply = call(MeshBridgeProtocol.OP_MESH_IP)

    /** Summarised state for the UI. */
    suspend fun state(): MeshBridgeState = when (val r = status()) {
        is BridgeReply.Success -> MeshBridgeState.Connected(MeshBridgeProtocol.parseStatus(r.result))
        is BridgeReply.Failure -> MeshBridgeState.Unavailable(r.kind)
    }

    private fun buildRequest(op: String): String {
        val rid = "vb-" + UUID.randomUUID()
        val nid = nodeId
        return if (nid != null) {
            MeshBridgeProtocol.buildEnvelope(
                actionRef = op, requestId = rid, targetNodeRef = nid,
                nonce = UUID.randomUUID().toString(), timestampMs = System.currentTimeMillis(),
            ).toString()
        } else {
            // Node id unknown yet: legacy request; the reply carries node_id.
            MeshBridgeProtocol.buildLegacyRequest(op, rid, null, timeoutMs).toString()
        }
    }

    private suspend fun call(op: String): BridgeReply = withContext(Dispatchers.IO) {
        if (op !in MeshBridgeProtocol.ALLOWED_OPS) {
            return@withContext BridgeReply.Failure(BridgeErrorKind.UNSUPPORTED, "op not permitted")
        }
        if (!MeshReachability.isMeshAppInstalled(app)) {
            return@withContext BridgeReply.Failure(BridgeErrorKind.NOT_INSTALLED, "not installed")
        }
        val reply = CompletableDeferred<String?>()
        val bound = CompletableDeferred<Messenger?>()
        val thread = HandlerThread("mesh-bridge").apply { start() }
        val replyTo = Messenger(object : Handler(thread.looper) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MeshBridgeProtocol.MSG_RESPONSE) {
                    reply.complete(try { msg.data?.getString(MeshBridgeProtocol.KEY_RESPONSE) } catch (_: Exception) { null })
                }
            }
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                bound.complete(service?.let { Messenger(it) })
            }
            override fun onServiceDisconnected(name: ComponentName?) { reply.complete(null) }
            override fun onBindingDied(name: ComponentName?) { bound.complete(null); reply.complete(null) }
            override fun onNullBinding(name: ComponentName?) { bound.complete(null) }
        }
        var didBind = false
        try {
            val intent = Intent(MeshBridgeProtocol.ACTION_BRIDGE).setPackage(MeshConfig.MESH_PACKAGE)
            didBind = try { app.bindService(intent, conn, Context.BIND_AUTO_CREATE) } catch (_: SecurityException) { false }
            if (!didBind) return@withContext BridgeReply.Failure(BridgeErrorKind.UNAVAILABLE, "bind refused")
            val messenger = withTimeoutOrNull(timeoutMs) { bound.await() }
                ?: return@withContext BridgeReply.Failure(BridgeErrorKind.UNAVAILABLE, "no binder")
            val msg = Message.obtain(null, MeshBridgeProtocol.MSG_REQUEST).apply {
                data = Bundle().apply { putString(MeshBridgeProtocol.KEY_REQUEST, buildRequest(op)) }
                this.replyTo = replyTo
            }
            try { messenger.send(msg) } catch (_: Exception) {
                return@withContext BridgeReply.Failure(BridgeErrorKind.UNAVAILABLE, "send failed")
            }
            val raw = withTimeoutOrNull(timeoutMs) { reply.await() }
                ?: return@withContext BridgeReply.Failure(BridgeErrorKind.TIMEOUT, "no reply")
            val parsed = MeshBridgeProtocol.parseResponse(raw)
            when (parsed) {
                is BridgeReply.Success -> parsed.nodeId?.let { nodeId = it }
                is BridgeReply.Failure -> parsed.nodeId?.let { nodeId = it }
            }
            parsed
        } catch (_: Exception) {
            BridgeReply.Failure(BridgeErrorKind.UNAVAILABLE, "bridge error")
        } finally {
            if (didBind) try { app.unbindService(conn) } catch (_: Exception) {}
            thread.quitSafely()
        }
    }
}
