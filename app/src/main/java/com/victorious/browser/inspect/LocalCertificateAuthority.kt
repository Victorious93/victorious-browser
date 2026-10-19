package com.victorious.browser.inspect

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the pre-baked local root certificate (assets/inspect_ca.p12, public half
 * also in res/raw/inspect_root.pem) and issues per-host leaf certificates with
 * BouncyCastle. Used ONLY to inspect TLS traffic from this app's OWN in-app WebView.
 *
 * Trust is app-scoped via res/xml/network_security_config referencing @raw/inspect_root.
 * There is NO system-wide / device-wide certificate installation.
 */
class LocalCertificateAuthority private constructor(
    val rootCert: X509Certificate,
    private val rootKey: PrivateKey,
    private val keyGen: KeyPairGenerator,
) {
    private val leafCache = ConcurrentHashMap<String, KeyStore>()

    /** Build (or reuse) a leaf KeyStore for [host], signed by the local root. */
    fun keyStoreForHost(host: String, password: CharArray): KeyStore =
        leafCache.getOrPut(host) { generateLeaf(host, password) }

    private fun generateLeaf(host: String, password: CharArray): KeyStore {
        val leafPair = keyGen.generateKeyPair()
        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60 * 60 * 1000)
        val notAfter = Date(now + 825L * 24 * 60 * 60 * 1000)

        val subject = X500Name("CN=$host, O=Victorious Browser Local Inspection")
        val builder = JcaX509v3CertificateBuilder(
            X500Name(rootCert.subjectX500Principal.name),
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            leafPair.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )
        builder.addExtension(
            Extension.extendedKeyUsage,
            false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth),
        )
        builder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(GeneralName(GeneralName.dNSName, host)),
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(PROVIDER)
            .build(rootKey)
        val leafCert = JcaX509CertificateConverter()
            .setProvider(PROVIDER)
            .getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, password)
        ks.setKeyEntry(
            host,
            leafPair.private,
            password,
            arrayOf(leafCert, rootCert),
        )
        return ks
    }

    companion object {
        private val PROVIDER: BouncyCastleProvider by lazy {
            val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (existing is BouncyCastleProvider) {
                existing
            } else {
                BouncyCastleProvider().also { Security.insertProviderAt(it, 1) }
            }
        }

        /**
         * Load the pre-baked CA from assets/inspect_ca.p12. The matching public cert
         * lives in res/raw/inspect_root.pem and is trusted only by this app via
         * network_security_config.
         */
        fun load(context: Context, password: CharArray): LocalCertificateAuthority {
            // Touch provider so BouncyCastle is registered before we use it.
            @Suppress("UNUSED_VARIABLE")
            val _p = PROVIDER

            val ks = KeyStore.getInstance("PKCS12")
            context.assets.open("inspect_ca.p12").use { ks.load(it, password) }
            val key = ks.getKey("root", password) as PrivateKey
            val cert = ks.getCertificate("root") as X509Certificate
            val keyGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            return LocalCertificateAuthority(cert, key, keyGen)
        }
    }
}
