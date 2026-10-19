package com.victorious.browser.sandbox

/**
 * Sandboxes, in the spirit of Hermit's "isolated containers for separating accounts" —
 * backed directly by `androidx.webkit`'s WebView Profile API (one Chromium profile per
 * sandbox: its own cookies, storage, geolocation permissions, service workers). There is
 * no separate persistence layer here beyond what the Profile API itself keeps — a
 * sandbox "exists" exactly when [androidx.webkit.ProfileStore] says it does, so this
 * code and the OS's own bookkeeping can never drift apart.
 *
 * [SandboxManager] in this same package is the Android-facing half; this file holds the
 * one thing worth unit-testing without an Android runtime: name validation.
 */
object SandboxName {
    /** androidx.webkit.Profile.DEFAULT_PROFILE_NAME — duplicated as a plain string so this
     *  object stays testable without the Android framework; [SandboxManager] cross-checks
     *  it against the real constant once, at startup, via [SandboxManager.assertReservedNameMatches]. */
    const val DEFAULT_PROFILE_NAME = "Default"

    private val VALID = Regex("^[A-Za-z0-9_-]{1,50}$")

    /** True if [name] is safe to hand to the Profile API and isn't the reserved default profile. */
    fun isValid(name: String): Boolean = VALID.matches(name) && name != DEFAULT_PROFILE_NAME
}
