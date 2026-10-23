package com.victorious.browser.wireguard

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Wraps `com.wireguard.android.backend.GoBackend` — the official WireGuard-for-Android
 * embeddable tunnel engine (Apache-2.0; see NOTICE). This app implements [Tunnel]
 * itself as a singleton (one tunnel at a time, named "victorious").
 *
 * **Device VPN slot conflict, by design of the Android platform, not a bug here**:
 * Android allows exactly one active [VpnService] system-wide. This README/guide has
 * always said this browser's own local inspection proxy deliberately avoids
 * `VpnService` "because the device's single VPN/tun slot is reserved for a separate
 * mesh-networking app" (Victorious Mesh). Turning this tunnel on takes that slot —
 * if Victorious Mesh's VPN is active, the OS tears it down the moment this one comes
 * up (and vice versa); there is no way for two VpnServices to coexist, in this app or
 * any other. [WireGuardScreen] surfaces this plainly before Connect; nothing here
 * coordinates with the Mesh app, because this app has no visibility into it.
 *
 * Not unit-tested: GoBackend's own methods are native (JNI, `wireguard-go`), require
 * a real `Context` and an actual tun device, and this has never run on a device or
 * emulator in this session.
 */
object WireGuardTunnelManager : Tunnel {
    private const val TAG = "WireGuardTunnelManager"
    private const val TUNNEL_NAME = "victorious"

    private var backend: GoBackend? = null
    private val _state = MutableStateFlow(Tunnel.State.DOWN)
    val state: StateFlow<Tunnel.State> = _state.asStateFlow()

    override fun getName(): String = TUNNEL_NAME

    override fun onStateChange(newState: Tunnel.State) {
        _state.value = newState
    }

    private fun backendFor(context: Context): GoBackend =
        backend ?: GoBackend(context.applicationContext).also { backend = it }

    /**
     * Android's standard per-app VPN consent flow: non-null means the caller must
     * launch this [Intent] for a result before [connect] will succeed; null means
     * consent is already granted (or was granted in an earlier run).
     */
    fun prepareIntent(context: Context): Intent? = VpnService.prepare(context)

    suspend fun connect(context: Context, fields: WireGuardProfileFields): Result<Unit> = withContext(Dispatchers.IO) {
        val parsed = WireGuardConfigBuilder.parse(fields)
        if (parsed.isFailure) return@withContext Result.failure(parsed.exceptionOrNull()!!)
        try {
            backendFor(context).setState(this@WireGuardTunnelManager, Tunnel.State.UP, parsed.getOrThrow())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring tunnel up", e)
            Result.failure(e)
        }
    }

    /**
     * Verified by decompiling `GoBackend.setState`: on a DOWN transition it ignores
     * the config argument entirely (hardcodes null internally before calling
     * `setStateInternal`), so passing null here is not a guess — it's what the
     * library's own public method does regardless of what's passed.
     */
    suspend fun disconnect(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            backendFor(context).setState(this@WireGuardTunnelManager, Tunnel.State.DOWN, null)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bring tunnel down", e)
            Result.failure(e)
        }
    }
}
