package com.victorious.browser.netid

import org.littleshoot.proxy.ChainedProxy
import org.littleshoot.proxy.ChainedProxyAdapter
import org.littleshoot.proxy.ChainedProxyManager
import org.littleshoot.proxy.ChainedProxyType
import org.littleshoot.proxy.impl.ClientDetails
import io.netty.handler.codec.http.HttpRequest
import java.net.InetSocketAddress
import java.util.Queue
import java.util.concurrent.atomic.AtomicReference

/**
 * Live upstream-proxy chain for LittleProxy. Holds the current [UpstreamProxyConfig]
 * so the local proxy can be restarted OR hot-updated without recreating the manager.
 */
object UpstreamChain {
    private val current = AtomicReference(UpstreamProxyConfig.OFF)

    fun set(config: UpstreamProxyConfig) {
        current.set(config)
    }

    fun get(): UpstreamProxyConfig = current.get()

    fun manager(): ChainedProxyManager = ChainedProxyManager { _: HttpRequest, queue: Queue<ChainedProxy>, _: ClientDetails ->
        val cfg = current.get()
        if (cfg.enabled && cfg.host.isNotBlank() && cfg.port in 1..65535) {
            queue.add(object : ChainedProxyAdapter() {
                override fun getChainedProxyAddress(): InetSocketAddress =
                    InetSocketAddress(cfg.host, cfg.port)

                override fun getChainedProxyType(): ChainedProxyType = when (cfg.type) {
                    UpstreamProxyConfig.Type.HTTP -> ChainedProxyType.HTTP
                    UpstreamProxyConfig.Type.HTTPS -> ChainedProxyType.HTTP // CONNECT over TLS not in LittleProxy 2.2; treat as HTTP CONNECT
                    UpstreamProxyConfig.Type.SOCKS5 -> ChainedProxyType.SOCKS5
                }

                override fun getUsername(): String? = cfg.username.ifEmpty { null }
                override fun getPassword(): String? = if (cfg.hasAuth) cfg.password else null
            })
        }
        // Always allow falling back to direct if the upstream fails / is disabled.
        queue.add(ChainedProxyAdapter.FALLBACK_TO_DIRECT_CONNECTION)
    }

    /**
     * Note on HTTPS type: LittleProxy 2.2's ChainedProxyType has HTTP / SOCKS4 / SOCKS5
     * only. There is no first-class "HTTPS upstream" (TLS-to-proxy) type. We map HTTPS
     * to HTTP CONNECT and document the limitation in the UI. [Guessing] a custom
     * SslEngineSource on the chained proxy might enable TLS-to-proxy, but that is not
     * wired here.
     */
    const val HTTPS_LIMITATION =
        "HTTPS upstream (TLS to the proxy itself) is not fully supported by LittleProxy 2.2; " +
            "the HTTPS type is stored for UI/config and currently chains as HTTP CONNECT."
}
