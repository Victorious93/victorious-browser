package com.victorious.browser.evidence

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serialize / deserialize an [EvidenceCase] to the per-case index.json.
 *
 * Pure logic (org.json ships with the Android/robolectric-free unit test runtime
 * via the json stub, but to stay fully testable on a plain JVM we keep parsing
 * tolerant). Round-trips every field of every item.
 */
object EvidenceIndex {

    const val VERSION = 1
    const val INDEX_FILE = "index.json"

    fun toJson(case: EvidenceCase): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("id", case.id)
        root.put("name", case.name)
        root.put("createdAt", case.createdAt)
        val arr = JSONArray()
        for (item in case.items) {
            val o = JSONObject()
            o.put("id", item.id)
            o.put("type", item.type.name)
            o.put("timestamp", item.timestamp)
            o.put("host", item.host)
            o.put("title", item.title)
            o.put("fileRef", item.fileRef ?: JSONObject.NULL)
            o.put("data", item.data ?: JSONObject.NULL)
            o.put("note", item.note ?: JSONObject.NULL)
            arr.put(o)
        }
        root.put("items", arr)
        return root.toString(2)
    }

    fun fromJson(json: String): EvidenceCase {
        val root = JSONObject(json)
        val items = mutableListOf<EvidenceItem>()
        val arr = root.optJSONArray("items") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            items.add(
                EvidenceItem(
                    id = o.optString("id"),
                    type = EvidenceType.fromString(o.optString("type")),
                    timestamp = o.optLong("timestamp"),
                    host = o.optString("host"),
                    title = o.optString("title"),
                    fileRef = o.optNullString("fileRef"),
                    data = o.optNullString("data"),
                    note = o.optNullString("note"),
                ),
            )
        }
        return EvidenceCase(
            id = root.optString("id"),
            name = root.optString("name"),
            createdAt = root.optLong("createdAt"),
            items = items,
        )
    }

    private fun JSONObject.optNullString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.ifEmpty { null }
    }
}
