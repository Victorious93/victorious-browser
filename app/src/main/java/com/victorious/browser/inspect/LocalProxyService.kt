package com.victorious.browser.inspect

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.victorious.browser.netid.NetIdSettings
import com.victorious.browser.netid.UpstreamChain
import org.littleshoot.proxy.HttpProxyServer
import org.littleshoot.proxy.TransportProtocol
import org.littleshoot.proxy.impl.DefaultHttpProxyServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device local HTTP(S) inspection proxy bound to 127.0.0.1:<port>.
 *
 * This is an ordinary localhost socket proxy. It deliberately does NOT use
 * Android VpnService — the device's single VPN/tun slot is reserved for a
 * separate mesh-networking app, so a VpnService-based capture is disallowed.
 * The localhost-proxy approach coexists with that VPN.
 *
 * LittleProxy on Android ART: we force Netty's NIO transport via
 * [TransportProtocol.TCP] (which uses NioEventLoopGroup / SelectorProvider).
 * Native epoll transport is unavailable on Android and is never selected by
 * LittleProxy's TCP path (UDP/UDT is the only alternative, unused here).
 */
object LocalProxyService {
    private const val TAG = "LocalProxyService"
    private const val DEFAULT_PORT = 8888
    private val CA_PASSWORD = "inspect".toCharArray()

    private val running = AtomicBoolean(false)
    private var server: HttpProxyServer? = null
    private var ca: LocalCertificateAuthority? = null
    private var boundPort: Int = DEFAULT_PORT

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val port: Int get() = boundPort

    /**
     * Start the localhost proxy and (if supported) route this app's WebView
     * through it via [ProxyController]. Returns true on success.
     *
     * Toggled from the Network UI and from the fleet control bridge
     * (`browser.proxy.set` / `browser.proxy.status`).
     */
    @Synchronized
    fun start(context: Context, preferredPort: Int = DEFAULT_PORT): Boolean {
        if (running.get()) return true
        return try {
            // Root is pre-baked in assets/inspect_ca.p12; public half is in
            // res/raw/inspect_root.pem and trusted only by this app via
            // network_security_config (no system-wide install).
            // Apply any persisted upstream chain before bootstrap (off by default).
            UpstreamChain.set(NetIdSettings.get(context).upstream.value)
            val authority = LocalCertificateAuthority.load(context, CA_PASSWORD)
            val mitm = LocalMitmManager(authority)
            val listen = InetSocketAddress("127.0.0.1", preferredPort)

            // Force NIO transport (TCP). Do not touch UDT / epoll.
            val proxy = DefaultHttpProxyServer.bootstrap()
                .withName("VictoriousBrowser-Inspect")
                .withAddress(listen)
                .withAllowLocalOnly(true)
                .withTransportProtocol(TransportProtocol.TCP)
                .withManInTheMiddle(mitm)
                .withFiltersSource(CaptureFiltersSource(ContentBlockerSettings.get(context.applicationContext)))
                .withChainProxyManager(UpstreamChain.manager())
                .withTransparent(false)
                .start()

            server = proxy
            ca = authority
            boundPort = proxy.listenAddress.port
            running.set(true)
            _isRunning.value = true

            applyWebViewProxy(boundPort)
            Log.i(TAG, "Local inspection proxy listening on 127.0.0.1:$boundPort (NIO/TCP)")
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start local inspection proxy", t)
            stop()
            false
        }
    }

    @Synchronized
    fun stop() {
        try {
            server?.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping proxy", t)
        }
        server = null
        running.set(false)
        _isRunning.value = false
        clearWebViewProxy()
        Log.i(TAG, "Local inspection proxy stopped")
    }

    private fun applyWebViewProxy(port: Int) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            Log.w(TAG, "PROXY_OVERRIDE not supported on this WebView; proxy is up but WebView is not routed")
            return
        }
        val config = ProxyConfig.Builder()
            .addProxyRule("127.0.0.1:$port")
            // Bypass for localhost so the proxy itself and loopback dashboards stay direct.
            .addDirect("localhost")
            .addDirect("127.0.0.1")
            .addDirect("[::1]")
            .build()
        ProxyController.getInstance().setProxyOverride(config, { /* executor */ }, {
            Log.i(TAG, "WebView ProxyController override applied -> 127.0.0.1:$port")
        })
    }

    private fun clearWebViewProxy() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
        try {
            ProxyController.getInstance().clearProxyOverride({ /* executor */ }, {
                Log.i(TAG, "WebView ProxyController override cleared")
            })
        } catch (t: Throwable) {
            Log.w(TAG, "clearProxyOverride failed", t)
        }
    }
}
