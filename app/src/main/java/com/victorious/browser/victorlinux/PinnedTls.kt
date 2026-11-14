package com.victorious.browser.victorlinux

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * TLS for ONE saved PC. There is no trust-all anywhere:
 *  - no pin: the platform's normal CA validation and host-name verification apply;
 *  - with a pin: the chain is accepted only if the leaf certificate's SHA-256 equals the pin the owner
 *    confirmed (self-signed PCs), it is within its validity dates, AND the normal host-name check still passes;
 *    anything else is rejected.
 */
object PinnedTls {
    fun sha256(cert: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }

    class PinTrustManager(private val pinHex: String, private val fallback: X509TrustManager?) : X509TrustManager {
        private val pin = Fingerprint.normalize(pinHex)
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw CertificateException("client certificates are not used")
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate presented")
            if (sha256(leaf) != pin) throw CertificateException("certificate does not match the pinned SHA-256")
            leaf.checkValidity()
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = fallback?.acceptedIssuers ?: emptyArray()
    }

    fun systemTrustManager(): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(null as KeyStore?)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    fun socketFactory(pinHex: String?): SSLSocketFactory? {
        if (pinHex == null) return null   // use the platform default: normal validation
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(PinTrustManager(pinHex, null)), null)
        return ctx.socketFactory
    }

    /** Always the platform default verifier: the certificate must also be valid for THIS host or IP. */
    fun hostnameVerifier(): HostnameVerifier = HttpsURLConnection.getDefaultHostnameVerifier()
}
