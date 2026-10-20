package com.victorious.browser.certmgr

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.security.KeyChain

/**
 * Builds the system intents for the GUIDED "install CA" flow.
 *
 * IMPORTANT: Android does NOT allow an app to silently install a user CA. These
 * intents only LAUNCH the OS trusted-credential installer / security settings;
 * the user must confirm the install themselves. Apps that target modern Android
 * do not trust user-installed CAs by default — this browser already trusts the
 * local root via its own network_security_config, but OTHER apps will not.
 */
object CaInstallHelper {

    /**
     * KeyChain install intent pre-loaded with the active CA DER bytes. Launching
     * it opens the OS "Install a certificate" prompt. May be null on OEMs that
     * don't resolve it; callers should fall back to [securitySettingsIntent].
     */
    fun installIntent(context: Context): Intent? {
        return try {
            val der = LocalCaProvider.activeRootDer(context)
            KeyChain.createInstallIntent().apply {
                putExtra(KeyChain.EXTRA_CERTIFICATE, der)
                putExtra(KeyChain.EXTRA_NAME, "Victorious Browser Local Inspection CA")
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** Fallback: open the system Security settings so the user can install manually. */
    fun securitySettingsIntent(): Intent =
        Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** The on-screen guidance steps shown alongside the install buttons. */
    val steps: List<String> = listOf(
        "1. Tap \"Export CA\" to save the certificate to Downloads.",
        "2. Tap \"Install CA\" — this opens the OS certificate installer (we cannot install it for you).",
        "3. Confirm the install in the system prompt; choose \"CA certificate\" if asked.",
        "4. If the installer doesn't open, use \"Open Security settings\" → Encryption & credentials → Install a certificate → CA certificate, and pick the exported file.",
        "Note: user-installed CAs are shown under Settings as not trusted by apps targeting modern Android. This browser trusts the local root for its OWN WebView only; other apps will not.",
    )
}
