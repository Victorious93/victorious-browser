package com.victorious.browser.bridge

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.util.Log
import org.json.JSONObject

/**
 * Bound Messenger bridge for EXTERNAL_NODE fleet control.
 *
 * Check order on each request:
 *  1. caller-identity via Message.sendingUid + package + signing-cert allowlist
 *  2. envelope parse (legacy {id,op,params} OR frozen v1 envelope)
 *  3. for v1: ttl/expiry + nonce replay
 *  4. external-control switch for ops with remoteAllowed=false (and all legacy ops)
 *  5. dispatch
 *
 * External control remains OFF by default. Identity gate is never weakened.
 */
class FleetBridgeService : Service() {
    private lateinit var workerThread: HandlerThread
    private lateinit var messenger: Messenger
    private lateinit var settings: BridgeSettings
    private lateinit var dispatcher: BridgeDispatcher
    private val nonceCache = NonceCache()

    override fun onCreate() {
        super.onCreate()
        settings = BridgeSettings.get(this)
        dispatcher = BridgeDispatcher(this)
        workerThread = HandlerThread("fleet-bridge").also { it.start() }
        val handler = object : Handler(workerThread.looper) {
            override fun handleMessage(msg: Message) {
                handle(msg)
            }
        }
        messenger = Messenger(handler)
        Log.i(TAG, "FleetBridgeService ready node=${settings.nodeId}")
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != null && intent.action != BridgeProtocol.ACTION) {
            Log.w(TAG, "reject bind for unexpected action=${intent.action}")
            return null
        }
        return messenger.binder
    }

    override fun onDestroy() {
        workerThread.quitSafely()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        if (msg.what != BridgeProtocol.MSG_WHAT_REQUEST) {
            Log.w(TAG, "ignore what=${msg.what}")
            return
        }
        val replyTo = msg.replyTo
        val line = msg.data?.getString(BridgeProtocol.BUNDLE_LINE).orEmpty()

        // 1) Caller identity — Message.sendingUid (NOT Binder.getCallingUid()).
        val sendingUid = msg.sendingUid
        val verdict = CallerVerifier.verify(this, sendingUid)
        if (!verdict.allowed) {
            reply(replyTo, rejectLine(line, BridgeErrorCategory.NOT_PAIRED, "caller not allowlisted: ${verdict.reason}"))
            return
        }

        // 2–5) Envelope + guards + dispatch.
        val response = try {
            if (line.isBlank()) {
                BridgeCodec.err("_", BridgeErrorCategory.INVALID_PARAMETERS, "empty request line")
            } else {
                route(line)
            }
        } catch (t: V1Invalid) {
            BridgeCodec.err(
                safeRequestId(line),
                BridgeErrorCategory.INVALID_PARAMETERS,
                t.message ?: "invalid_parameters",
            )
        } catch (t: Throwable) {
            Log.e(TAG, "bad envelope", t)
            BridgeCodec.err(
                safeRequestId(line),
                BridgeErrorCategory.INVALID_PARAMETERS,
                t.message ?: "invalid envelope",
            )
        }
        reply(replyTo, BridgeCodec.encodeResponse(response))
    }

    private fun route(line: String): BridgeResponse {
        val o = JSONObject(line.trim())
        return if (BridgeV1.isV1Object(o)) {
            routeV1(o)
        } else {
            routeLegacy(line)
        }
    }

    private fun routeLegacy(line: String): BridgeResponse {
        // Legacy ops all require external control (remoteAllowed=false semantics).
        if (!settings.isExternalControlEnabled()) {
            return BridgeCodec.err(
                safeId(line),
                BridgeErrorCategory.NOT_PAIRED,
                "external control is off",
            )
        }
        val req = BridgeCodec.parseRequestLine(line)
        return dispatcher.dispatch(req)
    }

    private fun routeV1(o: JSONObject): BridgeResponse {
        val env = EnvelopeParser.parse(o)
        val now = System.currentTimeMillis()

        if (BridgeV1.isExpired(now, env.timestampMs, env.ttlMs)) {
            return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "request expired",
            )
        }
        if (!nonceCache.checkAndRemember(env.nonce, env.timestampMs, env.ttlMs)) {
            return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.INVALID_PARAMETERS,
                "nonce replay or expired",
            )
        }

        // Optional targetNodeRef match when present.
        if (env.targetNodeRef != null && env.targetNodeRef != settings.nodeId) {
            return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.NOT_FOUND,
                "targetNodeRef mismatch",
            )
        }

        if (CapabilitiesRegistry.requiresExternalControl(env.op) &&
            !settings.isExternalControlEnabled()
        ) {
            return BridgeCodec.err(
                env.requestId,
                BridgeErrorCategory.NOT_PAIRED,
                "external control is off",
            )
        }

        return dispatcher.dispatchV1(env)
    }

    private fun rejectLine(requestLine: String, category: BridgeErrorCategory, message: String): String {
        val id = safeRequestId(requestLine)
        return BridgeCodec.encodeResponse(BridgeCodec.err(id, category, message))
    }

    private fun safeId(line: String): String = try {
        if (line.isBlank()) "_" else BridgeCodec.parseRequestLine(line).id
    } catch (_: Exception) {
        "_"
    }

    private fun safeRequestId(line: String): String {
        return try {
            if (line.isBlank()) return "_"
            val o = JSONObject(line.trim())
            when {
                o.has("requestId") -> o.optString("requestId", "_").ifEmpty { "_" }
                o.has("id") -> o.optString("id", "_").ifEmpty { "_" }
                else -> "_"
            }
        } catch (_: Exception) {
            "_"
        }
    }

    private fun reply(replyTo: Messenger?, line: String) {
        if (replyTo == null) {
            Log.w(TAG, "no replyTo; dropping response")
            return
        }
        try {
            val out = Message.obtain(null, BridgeProtocol.MSG_WHAT_RESPONSE).also {
                it.data = Bundle().apply { putString(BridgeProtocol.BUNDLE_LINE, line) }
            }
            replyTo.send(out)
        } catch (t: Throwable) {
            Log.e(TAG, "reply failed", t)
        }
    }

    companion object {
        private const val TAG = "FleetBridgeService"
    }
}
