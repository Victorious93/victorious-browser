package com.victorious.browser.evidence

/**
 * Data model for the recon "evidence case" feature. Pure Kotlin (no Android) so
 * the models + index serialization can be unit-tested.
 *
 * An evidence CASE is one investigation; it owns a timestamped folder under the
 * app files dir and an index.json listing its ITEMS. Each item is a screenshot,
 * a (secret-stripped) request/response exchange, a scanner finding, or a note.
 */
enum class EvidenceType {
    SCREENSHOT,
    REQUEST,
    FINDING,
    NOTE;

    companion object {
        fun fromString(s: String?): EvidenceType =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: NOTE
    }
}

/**
 * A single evidence item.
 *
 * @param fileRef relative file name inside the case folder (e.g. a PNG), or null
 *                for items whose content is inline in [data].
 * @param data    inline textual payload (already secret-stripped for REQUEST).
 */
data class EvidenceItem(
    val id: String,
    val type: EvidenceType,
    val timestamp: Long,
    val host: String,
    val title: String,
    val fileRef: String? = null,
    val data: String? = null,
    val note: String? = null,
)

/** A case: metadata + its items. */
data class EvidenceCase(
    val id: String,
    val name: String,
    val createdAt: Long,
    val items: List<EvidenceItem> = emptyList(),
) {
    fun withItem(item: EvidenceItem): EvidenceCase = copy(items = items + item)

    fun countByType(type: EvidenceType): Int = items.count { it.type == type }
}
