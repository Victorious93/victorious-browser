package com.victorious.browser.scanner

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted scanner configuration: the in-scope host allowlist and the optional
 * OOB collaborator settings. SharedPreferences, same pattern as
 * [com.victorious.browser.urltools.VirusTotalSettings].
 *
 * SAFEGUARD: every intrusive action in this module is gated on [scopeHosts] via
 * [ScopeMatcher]. The allowlist is empty by default, so nothing can run until the
 * user explicitly adds a target host. Browsing traffic never triggers a scan.
 */
class ScannerConfig private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _scopeHosts = MutableStateFlow(
        prefs.getStringSet(KEY_SCOPE, emptySet())?.toSortedSet()?.toList().orEmpty(),
    )
    val scopeHosts: StateFlow<List<String>> = _scopeHosts.asStateFlow()

    private val _collaborator = MutableStateFlow(
        Collaborator.Config(
            domain = prefs.getString(KEY_OOB_DOMAIN, "").orEmpty(),
            pollUrl = prefs.getString(KEY_OOB_POLL, "").orEmpty(),
        ),
    )
    val collaborator: StateFlow<Collaborator.Config> = _collaborator.asStateFlow()

    fun addScopeHost(raw: String) {
        val norm = ScopeMatcher.normalizeEntry(raw)
        if (norm.isEmpty()) return
        val next = (_scopeHosts.value + norm).distinct().sorted()
        persistScope(next)
    }

    fun removeScopeHost(entry: String) {
        persistScope(_scopeHosts.value.filterNot { it == entry })
    }

    fun clearScope() = persistScope(emptyList())

    private fun persistScope(list: List<String>) {
        prefs.edit().putStringSet(KEY_SCOPE, list.toSet()).apply()
        _scopeHosts.value = list
    }

    fun setCollaborator(domain: String, pollUrl: String) {
        val cfg = Collaborator.Config(domain.trim(), pollUrl.trim())
        prefs.edit()
            .putString(KEY_OOB_DOMAIN, cfg.domain)
            .putString(KEY_OOB_POLL, cfg.pollUrl)
            .apply()
        _collaborator.value = cfg
    }

    fun isInScope(url: String): Boolean = ScopeMatcher.inScope(url, _scopeHosts.value)

    companion object {
        private const val PREFS = "scanner_config"
        private const val KEY_SCOPE = "scope_hosts"
        private const val KEY_OOB_DOMAIN = "oob_domain"
        private const val KEY_OOB_POLL = "oob_poll_url"

        @Volatile private var instance: ScannerConfig? = null

        fun get(context: Context): ScannerConfig =
            instance ?: synchronized(this) {
                instance ?: ScannerConfig(context).also { instance = it }
            }
    }
}
