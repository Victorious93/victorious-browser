package com.victorious.browser.browser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One persisted (non-incognito) tab. Back-forward list / JS heap are not saved. */
data class PersistedTab(
    val id: String,
    val url: String,
    val title: String,
)

data class TabSessionSnapshot(
    val tabs: List<PersistedTab>,
    val activeId: String?,
)

/**
 * Encode/decode the open-tab list for process-death restore. Incognito tabs are
 * never written. URLs that wouldn't be recorded in history (about:blank, data:, …)
 * are dropped so a restore can't resurrect a blank trap.
 */
internal object TabSessionLogic {
    const val MAX_TABS = 20

    fun persistable(tab: BrowserTab): Boolean =
        !tab.incognito && HistoryLogic.isRecordable(tab.url)

    fun snapshot(tabs: List<BrowserTab>, activeId: String?): TabSessionSnapshot {
        val kept = tabs.filter(::persistable).take(MAX_TABS)
        val active = activeId?.takeIf { id -> kept.any { it.id == id } }
            ?: kept.lastOrNull()?.id
        return TabSessionSnapshot(
            tabs = kept.map { PersistedTab(it.id, it.url, it.title) },
            activeId = active,
        )
    }

    fun encode(snap: TabSessionSnapshot): String {
        val arr = JSONArray()
        snap.tabs.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id)
                    .put("url", it.url)
                    .put("title", it.title),
            )
        }
        return JSONObject()
            .put("tabs", arr)
            .put("activeId", snap.activeId ?: JSONObject.NULL)
            .toString()
    }

    fun decode(raw: String?): TabSessionSnapshot {
        if (raw.isNullOrBlank()) return TabSessionSnapshot(emptyList(), null)
        return try {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (i in 0 until minOf(arr.length(), MAX_TABS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url")
                    if (!HistoryLogic.isRecordable(url)) continue
                    val id = o.optString("id").ifBlank { "restored-$i" }
                    add(PersistedTab(id, url, o.optString("title")))
                }
            }
            val active = root.optString("activeId").takeIf { it.isNotBlank() && tabs.any { t -> t.id == it } }
            TabSessionSnapshot(tabs, active ?: tabs.lastOrNull()?.id)
        } catch (_: Exception) {
            TabSessionSnapshot(emptyList(), null)
        }
    }

    fun toBrowserTabs(snap: TabSessionSnapshot): Pair<List<BrowserTab>, String?> {
        val tabs = snap.tabs.map {
            BrowserTab(id = it.id, title = it.title, url = it.url, incognito = false)
        }
        val active = snap.activeId?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.lastOrNull()?.id
        return tabs to active
    }
}

/** SharedPreferences-backed session of open (non-private) tabs. */
class TabSessionStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): TabSessionSnapshot = TabSessionLogic.decode(prefs.getString(KEY, null))

    fun save(tabs: List<BrowserTab>, activeId: String?) {
        val snap = TabSessionLogic.snapshot(tabs, activeId)
        prefs.edit().putString(KEY, TabSessionLogic.encode(snap)).apply()
    }

    /** Forget the saved session (Fire / clear on exit). */
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    fun restoreInto(store: TabStore) {
        val (tabs, active) = TabSessionLogic.toBrowserTabs(load())
        if (tabs.isEmpty()) return
        store.restore(tabs, active)
    }

    companion object {
        private const val PREFS = "browser_tabs"
        private const val KEY = "session_json"

        @Volatile private var instance: TabSessionStore? = null
        fun get(context: Context): TabSessionStore =
            instance ?: synchronized(this) {
                instance ?: TabSessionStore(context).also { instance = it }
            }
    }
}
