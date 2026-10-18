package com.victorious.browser.bridge

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.util.Log

/**
 * Bound Messenger service — fleet control bridge (EXTERNAL_NODE).
 *
 * Intent action: [BridgeProtocol.ACTION]
 * Message.what = 1 request / 2 response; payload in Bundle key [BridgeProtocol.BUNDLE_LINE]
 * as a single newline-delimited JSON line.
 *
 * Security check order on each request:
 *  1. caller-identity via Message.sendingUid + package + signing-cert allowlist
 *  2. external-control-enabled switch (OFF by default) → not_paired
 *  3. envelope validity / op dispatch
 */
class FleetBridgeService : Service() {
    private lateinit var workerThread: HandlerThread
    private lateinit var messenger: Messenger
    private lateinit var dispatcher: BridgeDispatcher
    private lateinit var settings: BridgeSettings

    override fun onCreate() {
        super.onCreate()
        settings = BridgeSettings.get(this)
        dispatcher = BridgeDispatcher(applicationContext)
        workerThread = HandlerThread("FleetBridge", Process.THREAD_PRIORITY_DEFAULT).also { it.start() }
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

        // 2) External control switch (OFF by default).
        if (!settings.isExternalControlEnabled()) {
            reply(replyTo, rejectLine(line, BridgeErrorCategory.NOT_PAIRED, "external control is off"))
            return
        }

        // 3) Envelope validity + dispatch.
        val response = try {
            if (line.isBlank()) {
                BridgeCodec.err("_", BridgeErrorCategory.INVALID_PARAMETERS, "empty request line")
            } else {
                val req = BridgeCodec.parseRequestLine(line)
                dispatcher.dispatch(req)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "bad envelope", t)
            BridgeCodec.err(
                safeId(line),
                BridgeErrorCategory.INVALID_PARAMETERS,
                t.message ?: "invalid envelope",
            )
        }
        reply(replyTo, BridgeCodec.encodeResponse(response))
    }

    private fun rejectLine(requestLine: String, category: BridgeErrorCategory, message: String): String {
        val id = safeId(requestLine)
        return BridgeCodec.encodeResponse(BridgeCodec.err(id, category, message))
    }

    private fun safeId(line: String): String = try {
        if (line.isBlank()) "_" else BridgeCodec.parseRequestLine(line).id
    } catch (_: Exception) {
        "_"
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
