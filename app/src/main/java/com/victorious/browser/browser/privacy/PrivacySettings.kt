package com.victorious.browser.browser.privacy

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted "Privacy protections" toggles. Every protection defaults OFF: this is a
 * recon browser and trackers / raw URLs / referrers are often the signal you want.
 */
class PrivacySettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun flag(key: String) = MutableStateFlow(prefs.getBoolean(key, false))

    private val _blockTrackers = flag(K_BLOCK)
    private val _forceHttps = flag(K_HTTPS)
    private val _gpc = flag(K_GPC)
    private val _stripParams = flag(K_STRIP)
    private val _trimReferrer = flag(K_REF)
    private val _cookiePopups = flag(K_COOKIE)
    private val _clearOnExit = flag(K_EXIT)
    private val _fireproof = MutableStateFlow(prefs.getStringSet(K_FIREPROOF, emptySet())!!.toSet())
    private val _allowlist = MutableStateFlow(prefs.getStringSet(K_ALLOW, emptySet())!!.toSet())

    val blockTrackers: StateFlow<Boolean> = _blockTrackers.asStateFlow()
    val forceHttps: StateFlow<Boolean> = _forceHttps.asStateFlow()
    val gpc: StateFlow<Boolean> = _gpc.asStateFlow()
    val stripParams: StateFlow<Boolean> = _stripParams.asStateFlow()
    val trimReferrer: StateFlow<Boolean> = _trimReferrer.asStateFlow()
    val cookiePopups: StateFlow<Boolean> = _cookiePopups.asStateFlow()
    val clearOnExit: StateFlow<Boolean> = _clearOnExit.asStateFlow()
    /** Fireproofed hosts (suffix match). */
    val fireproof: StateFlow<Set<String>> = _fireproof.asStateFlow()
    /** Sites where tracker blocking is turned off (per-site exception). */
    val allowlist: StateFlow<Set<String>> = _allowlist.asStateFlow()

    fun setBlockTrackers(v: Boolean) = put(K_BLOCK, _blockTrackers, v)
    fun setForceHttps(v: Boolean) = put(K_HTTPS, _forceHttps, v)
    fun setGpc(v: Boolean) = put(K_GPC, _gpc, v)
    fun setStripParams(v: Boolean) = put(K_STRIP, _stripParams, v)
    fun setTrimReferrer(v: Boolean) = put(K_REF, _trimReferrer, v)
    fun setCookiePopups(v: Boolean) = put(K_COOKIE, _cookiePopups, v)
    fun setClearOnExit(v: Boolean) = put(K_EXIT, _clearOnExit, v)

    fun addFireproof(host: String) = putSet(K_FIREPROOF, _fireproof, FireproofLogic.add(_fireproof.value, host))
    fun removeFireproof(host: String) = putSet(K_FIREPROOF, _fireproof, FireproofLogic.remove(_fireproof.value, host))
    fun isFireproof(host: String?) = FireproofLogic.isFireproof(host, _fireproof.value)

    fun setAllowed(host: String, allowed: Boolean) {
        val h = HostUtil.normalizeHost(host) ?: return
        putSet(K_ALLOW, _allowlist, if (allowed) _allowlist.value + h else _allowlist.value - h)
    }
    fun isAllowed(host: String?): Boolean =
        host != null && _allowlist.value.any { HostUtil.matchesSuffix(host.lowercase(), it) }

    /** Any protection that needs a document-start script. */
    fun needsScripts() = _gpc.value || _trimReferrer.value

    private fun put(key: String, flow: MutableStateFlow<Boolean>, v: Boolean) {
        flow.value = v
        prefs.edit().putBoolean(key, v).apply()
    }

    private fun putSet(key: String, flow: MutableStateFlow<Set<String>>, v: Set<String>) {
        flow.value = v
        prefs.edit().putStringSet(key, v).apply()
    }

    companion object {
        private const val PREFS = "privacy_protections"
        private const val K_BLOCK = "block_trackers"
        private const val K_HTTPS = "force_https"
        private const val K_GPC = "gpc"
        private const val K_STRIP = "strip_params"
        private const val K_REF = "trim_referrer"
        private const val K_COOKIE = "cookie_popups"
        private const val K_EXIT = "clear_on_exit"
        private const val K_FIREPROOF = "fireproof_hosts"
        private const val K_ALLOW = "tracker_allowlist"

        @Volatile private var instance: PrivacySettings? = null
        fun get(context: Context): PrivacySettings =
            instance ?: synchronized(this) { instance ?: PrivacySettings(context).also { instance = it } }
    }
}
