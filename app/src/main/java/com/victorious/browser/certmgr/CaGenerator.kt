package com.victorious.browser.certmgr

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Generates a fresh self-signed local inspection root (CA) with BouncyCastle.
 *
 * Pure JVM — no Android Context. Used by the "regenerate CA" flow. Producing a
 * new root here INVALIDATES any CA cert the user previously exported/installed:
 * leaf certs signed by the old root will no longer chain to the installed trust
 * anchor. Callers must warn the user before persisting a regenerated root.
 */
object CaGenerator {

    /** Result of a root generation: the keypair + the self-signed cert. */
    data class CaMaterial(
        val keyPair: KeyPair,
        val certificate: X509Certificate,
    )

    private val PROVIDER: BouncyCastleProvider by lazy {
        val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
        if (existing is BouncyCastleProvider) existing
        else BouncyCastleProvider().also { Security.insertProviderAt(it, 1) }
    }

    /**
     * Generate a new self-signed RSA-2048 root valid for [validityDays] days.
     * Subject = "CN=[commonName], O=[org]". Marked CA:TRUE with keyCertSign.
     */
    fun generateRoot(
        commonName: String = "Victorious Browser Local Inspection Root",
        org: String = "Victorious Fleet",
        validityDays: Long = 3650L,
    ): CaMaterial {
        @Suppress("UNUSED_VARIABLE") val p = PROVIDER
        val keyGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val pair = keyGen.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60 * 60 * 1000)
        val notAfter = Date(now + validityDays * 24 * 60 * 60 * 1000)
        val subject = X500Name("CN=$commonName, O=$org")

        val builder = JcaX509v3CertificateBuilder(
            subject, // issuer == subject (self-signed)
            BigInteger.valueOf(now),
            notBefore,
            notAfter,
            subject,
            pair.public,
        )
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign or KeyUsage.digitalSignature),
        )
        val extUtils = JcaX509ExtensionUtils()
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extUtils.createSubjectKeyIdentifier(pair.public),
        )

        val signer = JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(PROVIDER)
            .build(pair.private)
        val cert = JcaX509CertificateConverter()
            .setProvider(PROVIDER)
            .getCertificate(builder.build(signer))
        return CaMaterial(pair, cert)
    }

    /** Build a PKCS12 KeyStore holding the root under alias "root". */
    fun toKeyStore(material: CaMaterial, password: CharArray): KeyStore {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, password)
        ks.setKeyEntry(
            "root",
            material.keyPair.private as PrivateKey,
            password,
            arrayOf(material.certificate),
        )
        return ks
    }
}
