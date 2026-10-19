package com.victorious.browser.bridge

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Identifies the bridge caller via [android.os.Message.sendingUid]
 * (NOT [android.os.Binder.getCallingUid] — Messenger delivers the peer uid on
 * the Message). Resolves package(s) for that uid and verifies the signing
 * certificate SHA-256 against [ControllerAllowlist].
 */
object CallerVerifier {
    private const val TAG = "CallerVerifier"

    data class Verdict(val allowed: Boolean, val packages: List<String>, val reason: String)

    fun verify(context: Context, sendingUid: Int): Verdict {
        if (sendingUid < 0) {
            return Verdict(false, emptyList(), "missing sendingUid")
        }
        val pm = context.packageManager
        val packages = pm.getPackagesForUid(sendingUid)?.toList().orEmpty()
        if (packages.isEmpty()) {
            return Verdict(false, emptyList(), "no packages for uid=$sendingUid")
        }
        val allowlistedPkgs = packages.filter { ControllerAllowlist.isAllowedPackage(it) }
        if (allowlistedPkgs.isEmpty()) {
            return Verdict(false, packages, "uid packages not in controller allowlist: $packages")
        }
        for (pkg in allowlistedPkgs) {
            val fingerprints = signingCertSha256s(pm, pkg)
            if (fingerprints.any { ControllerAllowlist.isAllowedCert(it) }) {
                return Verdict(true, listOf(pkg), "ok")
            }
            Log.w(TAG, "package $pkg certs not pinned: $fingerprints")
        }
        return Verdict(false, allowlistedPkgs, "signing cert not pinned for $allowlistedPkgs")
    }

    private fun signingCertSha256s(pm: PackageManager, packageName: String): List<String> {
        return try {
            val sigs = if (Build.VERSION.SDK_INT >= 28) {
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val si = info.signingInfo ?: return emptyList()
                if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                info.signatures
            }
            sigs?.filterNotNull()?.map { sha256Colon(it.toByteArray()) }.orEmpty()
        } catch (t: Throwable) {
            Log.e(TAG, "signingCertSha256s($packageName)", t)
            emptyList()
        }
    }

    private fun sha256Colon(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
