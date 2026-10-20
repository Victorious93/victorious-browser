package com.victorious.browser.sandbox

import android.util.Log
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Android-facing half of sandboxes: wraps `androidx.webkit`'s Profile API behind
 * [WebViewFeature.MULTI_PROFILE] feature detection, degrading to the single default
 * profile (i.e. no isolation) on a WebView that doesn't support it — never crashing,
 * never silently pretending isolation exists when it doesn't.
 */
object SandboxManager {
    private const val TAG = "SandboxManager"

    init {
        check(Profile.DEFAULT_PROFILE_NAME == SandboxName.DEFAULT_PROFILE_NAME) {
            "androidx.webkit.Profile.DEFAULT_PROFILE_NAME changed — update SandboxName"
        }
    }

    fun isSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    /** Sandbox names that currently exist, excluding the reserved default profile. */
    fun list(): List<String> {
        if (!isSupported()) return emptyList()
        return try {
            ProfileStore.getInstance().allProfileNames.filterNot { it == Profile.DEFAULT_PROFILE_NAME }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list sandboxes", e)
            emptyList()
        }
    }

    /** Creates [name] if it doesn't exist yet. Returns false if unsupported or the name is invalid. */
    fun create(name: String): Boolean {
        if (!isSupported() || !SandboxName.isValid(name)) return false
        return try {
            ProfileStore.getInstance().getOrCreateProfile(name)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create sandbox \"$name\"", e)
            false
        }
    }

    /**
     * Deletes [name]. The Profile API refuses to delete a profile a live WebView is
     * still assigned to — that surfaces here as a normal `false`, not a crash, so the
     * caller can tell the operator to close that sandbox's browser tab first.
     */
    fun delete(name: String): Boolean {
        if (!isSupported()) return false
        return try {
            ProfileStore.getInstance().deleteProfile(name)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete sandbox \"$name\" — likely still in use", e)
            false
        }
    }

    /**
     * Assigns [sandboxName] to [webView]. Must be called immediately after the WebView
     * is constructed and before its first `loadUrl` — the Profile API does not support
     * reassigning a profile mid-session. A null or invalid [sandboxName], or an
     * unsupported WebView, leaves the WebView on the default (unsandboxed) profile.
     */
    fun applyToNewWebView(webView: WebView, sandboxName: String?) {
        if (sandboxName == null || !isSupported() || !SandboxName.isValid(sandboxName)) return
        try {
            ProfileStore.getInstance().getOrCreateProfile(sandboxName)
            WebViewCompat.setProfile(webView, sandboxName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to assign sandbox \"$sandboxName\" — falling back to default profile", e)
        }
    }
}
