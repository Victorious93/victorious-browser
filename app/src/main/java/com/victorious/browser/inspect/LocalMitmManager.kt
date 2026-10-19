package com.victorious.browser.inspect

import io.netty.handler.codec.http.HttpRequest
import org.littleshoot.proxy.MitmManager
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.TrustManagerFactory

/**
 * LittleProxy [MitmManager] that terminates TLS from the app's own WebView using
 * per-host leaf certs from [LocalCertificateAuthority], and re-originates TLS to the
 * upstream server with the platform default trust store.
 */
class LocalMitmManager(
    private val ca: LocalCertificateAuthority,
) : MitmManager {

    private val password = "inspect".toCharArray()

    // proxy -> server: ordinary client TLS trusting the system CAs.
    private val upstreamContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply { init(null, null, null) }
    }

    /** client -> proxy: present a leaf cert for [peerHost] signed by our local root. */
    override fun serverSslEngine(peerHost: String?, peerPort: Int): SSLEngine {
        val host = peerHost ?: "localhost"
        val ks = ca.keyStoreForHost(host, password)
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, password)
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(kmf.keyManagers, null, null)
        return ctx.createSSLEngine().apply { useClientMode = false }
    }

    override fun serverSslEngine(): SSLEngine = serverSslEngine("localhost", 443)

    /** proxy -> upstream server leg. */
    override fun clientSslEngineFor(httpRequest: HttpRequest?, serverSslSession: SSLSession?): SSLEngine {
        return upstreamContext.createSSLEngine().apply { useClientMode = true }
    }

    @Suppress("unused")
    private fun systemTrust(): TrustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
}
