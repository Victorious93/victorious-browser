package com.victorious.browser.certmgr

import android.content.Context
import com.victorious.browser.export.MediaStoreDownloads
import java.security.cert.X509Certificate

/**
 * Exports the active local inspection CA certificate to the user's Downloads as
 * both PEM and DER/CRT, and surfaces the CA SHA-256 fingerprint.
 *
 * Only the PUBLIC certificate is ever exported — never the private key.
 */
object CaExportManager {

    data class ExportResult(
        val pemLocation: String?,
        val derLocation: String?,
        val fingerprint: String,
    )

    private const val SUBDIR = "VictoriousBrowser"

    fun fingerprint(context: Context): String =
        CertFormatter.sha256Fingerprint(LocalCaProvider.activeRootDer(context))

    fun fingerprintOf(cert: X509Certificate): String =
        CertFormatter.sha256Fingerprint(cert.encoded)

    /** Export both PEM and DER/CRT to Downloads/VictoriousBrowser. */
    fun exportBoth(context: Context, baseName: String = "victorious-inspection-ca"): ExportResult {
        val der = LocalCaProvider.activeRootDer(context)
        val safe = CertFormatter.safeBaseName(baseName)
        val pem = CertFormatter.toPem(der, "CERTIFICATE")

        val pemLoc = MediaStoreDownloads.write(
            context,
            displayName = "$safe.pem",
            mimeType = "application/x-pem-file",
            bytes = pem.toByteArray(Charsets.US_ASCII),
            subDir = SUBDIR,
        )
        val derLoc = MediaStoreDownloads.write(
            context,
            displayName = "$safe.crt",
            mimeType = "application/x-x509-ca-cert",
            bytes = der,
            subDir = SUBDIR,
        )
        return ExportResult(pemLoc, derLoc, CertFormatter.sha256Fingerprint(der))
    }
}
