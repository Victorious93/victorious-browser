package com.victorious.browser.certmgr

import android.content.Context
import com.victorious.browser.inspect.LocalCertificateAuthority
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate

/**
 * Resolves the ACTIVE local inspection CA for both the inspection proxy and the
 * cert-management screen.
 *
 * Preference order:
 *  1. A regenerated root persisted at filesDir/certmgr/regen_ca.p12 (if present).
 *  2. The pre-baked root in assets/inspect_ca.p12.
 *
 * Password matches the baked store ("inspect") so the regenerate flow reuses the
 * same KeyStore conventions and the proxy loads either transparently.
 */
object LocalCaProvider {

    val PASSWORD: CharArray get() = "inspect".toCharArray()
    private const val REGEN_DIR = "certmgr"
    private const val REGEN_FILE = "regen_ca.p12"

    private fun regenFile(context: Context): File =
        File(File(context.filesDir, REGEN_DIR), REGEN_FILE)

    fun hasRegenerated(context: Context): Boolean = regenFile(context).exists()

    /** Load the active CA authority (regenerated root preferred). */
    fun loadAuthority(context: Context): LocalCertificateAuthority {
        val f = regenFile(context)
        return if (f.exists()) {
            LocalCertificateAuthority.loadFromStream(f.inputStream(), PASSWORD)
        } else {
            LocalCertificateAuthority.load(context, PASSWORD)
        }
    }

    /** The active root certificate (for export / fingerprinting). */
    fun activeRootCert(context: Context): X509Certificate =
        loadAuthority(context).rootCert

    /** DER bytes of the active root certificate. */
    fun activeRootDer(context: Context): ByteArray = activeRootCert(context).encoded

    /**
     * Persist a freshly generated root, replacing any previous regenerated root.
     * Returns the new root certificate. INVALIDATES previously exported CA.
     */
    fun persistRegenerated(context: Context): X509Certificate {
        val material = CaGenerator.generateRoot()
        val ks: KeyStore = CaGenerator.toKeyStore(material, PASSWORD)
        val f = regenFile(context)
        f.parentFile?.mkdirs()
        f.outputStream().use { ks.store(it, PASSWORD) }
        return material.certificate
    }

    /** Delete the regenerated root, reverting to the baked-in asset root. */
    fun revertToBaked(context: Context): Boolean = regenFile(context).delete()
}
