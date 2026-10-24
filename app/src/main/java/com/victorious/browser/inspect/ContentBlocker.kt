package com.victorious.browser.inspect

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Content blocker, in the spirit of Hermit's "pick what to block" feature.
 *
 * Matching is host-suffix only (no regex, no full-URL rules) so the cost per
 * request stays O(depth-of-host). [SAMPLE_HOSTS] is a small, hand-picked
 * illustrative set per category — NOT a production blocklist. A real deployment
 * should swap this for a maintained list (e.g. an EasyList-format subscription)
 * behind the same [BlockCategory] -> host-suffix shape; see the Victorious
 * Framework guide, "Future improvements", for the update-mechanism TODO.
 */
enum class BlockCategory(val label: String) {
    ADWARE_MALWARE("Adware & malware"),
    COIN_MINERS("Coin miners"),
    TRACKERS("Trackers"),
    ADULT("Adult content"),
}

object ContentBlocker {
    private val SAMPLE_HOSTS: Map<BlockCategory, Set<String>> = mapOf(
        BlockCategory.ADWARE_MALWARE to setOf(
            "doubleclick.net",
            "adservice.google.com",
            "popads.net",
        ),
        BlockCategory.COIN_MINERS to setOf(
            "coinhive.com",
            "coin-hive.com",
            "cryptoloot.pro",
        ),
        BlockCategory.TRACKERS to setOf(
            "google-analytics.com",
            "googletagmanager.com",
            "scorecardresearch.com",
            "connect.facebook.net",
        ),
        BlockCategory.ADULT to emptySet(),
    )

    /** True if [host] equals, or is a subdomain of, any blocked entry in an enabled category. */
    fun isBlocked(host: String?, enabledCategories: Set<BlockCategory>): BlockCategory? {
        if (host.isNullOrBlank() || enabledCategories.isEmpty()) return null
        val h = host.lowercase().removeSuffix(".")
        for (category in enabledCategories) {
            val hosts = SAMPLE_HOSTS[category] ?: continue
            for (blocked in hosts) {
                if (h == blocked || h.endsWith(".$blocked")) return category
            }
        }
        return null
    }
}

/** Persisted blocker state: master switch + enabled categories (SharedPreferences, same pattern as BridgeSettings). */
class ContentBlockerSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, false))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _categories = MutableStateFlow(loadCategories())
    val categories: StateFlow<Set<BlockCategory>> = _categories.asStateFlow()

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    fun setCategoryEnabled(category: BlockCategory, value: Boolean) {
        val updated = if (value) _categories.value + category else _categories.value - category
        prefs.edit().putStringSet(KEY_CATEGORIES, updated.map { it.name }.toSet()).apply()
        _categories.value = updated
    }

    /** Categories to actually match against — empty unless the master switch is on. */
    fun activeCategories(): Set<BlockCategory> = if (_enabled.value) _categories.value else emptySet()

    private fun loadCategories(): Set<BlockCategory> {
        val stored = prefs.getStringSet(KEY_CATEGORIES, null) ?: return DEFAULT_CATEGORIES
        return stored.mapNotNull { runCatching { BlockCategory.valueOf(it) }.getOrNull() }.toSet()
    }

    companion object {
        private const val PREFS = "content_blocker"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_CATEGORIES = "categories"
        private val DEFAULT_CATEGORIES = setOf(BlockCategory.ADWARE_MALWARE, BlockCategory.COIN_MINERS)

        @Volatile private var instance: ContentBlockerSettings? = null

        fun get(context: Context): ContentBlockerSettings =
            instance ?: synchronized(this) {
                instance ?: ContentBlockerSettings(context).also { instance = it }
            }
    }
}
