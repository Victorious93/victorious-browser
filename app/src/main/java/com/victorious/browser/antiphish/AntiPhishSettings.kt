package com.victorious.browser.antiphish

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted anti-phish preferences: enable/disable passive detection, known-good
 * brand/domain list (editable), and brand-keyword map (serialized as "kw=domain" lines).
 *
 * Detection is OFF by default (advisory / non-blocking when on). No credentials stored.
 */
class AntiPhishSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _knownGood = MutableStateFlow(loadDomains())
    val knownGood: StateFlow<List<String>> = _knownGood.asStateFlow()

    private val _keywords = MutableStateFlow(loadKeywords())
    val keywords: StateFlow<Map<String, String>> = _keywords.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setKnownGood(domains: List<String>) {
        val cleaned = domains.map { DomainAnalyzer.normalizeHost(it) }
            .filter { it.isNotBlank() }
            .distinct()
        prefs.edit().putString(KEY_DOMAINS, cleaned.joinToString("\n")).apply()
        _knownGood.value = cleaned
    }

    fun setKeywords(map: Map<String, String>) {
        val cleaned = map
            .mapKeys { it.key.trim().lowercase() }
            .mapValues { DomainAnalyzer.normalizeHost(it.value) }
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
        val lines = cleaned.entries.joinToString("\n") { "${it.key}=${it.value}" }
        prefs.edit().putString(KEY_KEYWORDS, lines).apply()
        _keywords.value = cleaned
    }

    fun resetDefaults() {
        setKnownGood(KnownGoodBrands.DEFAULT_DOMAINS)
        setKeywords(KnownGoodBrands.DEFAULT_KEYWORDS)
    }

    private fun loadDomains(): List<String> {
        val raw = prefs.getString(KEY_DOMAINS, null)
        if (raw == null) return KnownGoodBrands.DEFAULT_DOMAINS
        return raw.lines().map { DomainAnalyzer.normalizeHost(it) }.filter { it.isNotBlank() }.distinct()
    }

    private fun loadKeywords(): Map<String, String> {
        val raw = prefs.getString(KEY_KEYWORDS, null)
        if (raw == null) return KnownGoodBrands.DEFAULT_KEYWORDS
        return raw.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val k = line.substring(0, i).trim().lowercase()
            val v = DomainAnalyzer.normalizeHost(line.substring(i + 1))
            if (k.isBlank() || v.isBlank()) null else k to v
        }.toMap()
    }

    companion object {
        private const val PREFS = "antiphish_settings"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_DOMAINS = "known_good_domains"
        private const val KEY_KEYWORDS = "brand_keywords"

        @Volatile private var instance: AntiPhishSettings? = null

        fun get(context: Context): AntiPhishSettings =
            instance ?: synchronized(this) {
                instance ?: AntiPhishSettings(context).also { instance = it }
            }
    }
}
