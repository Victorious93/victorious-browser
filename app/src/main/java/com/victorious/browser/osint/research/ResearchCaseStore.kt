package com.victorious.browser.osint.research

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * App-private persistence for research cases (filesDir/research/cases.json).
 * Nothing is saved automatically: every entry comes from an explicit "Save to case" tap.
 */
class ResearchCaseStore private constructor(context: Context) {
    private val file = File(context.applicationContext.filesDir, "research/cases.json")
    private val _cases = MutableStateFlow(load())
    val cases: StateFlow<List<ResearchCase>> = _cases.asStateFlow()
    private val _activeId = MutableStateFlow(_cases.value.lastOrNull()?.id)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private fun load(): List<ResearchCase> =
        if (file.exists()) ResearchCaseCodec.listFromJson(runCatching { file.readText() }.getOrDefault("[]")) else emptyList()

    @Synchronized
    private fun persist(list: List<ResearchCase>) {
        _cases.value = list
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "cases.json.tmp")
        tmp.writeText(ResearchCaseCodec.listToJson(list))
        tmp.renameTo(file)
    }

    fun create(name: String): ResearchCase {
        val c = ResearchCase(UUID.randomUUID().toString(), name.trim().ifBlank { "Case ${_cases.value.size + 1}" }, System.currentTimeMillis())
        persist(_cases.value + c)
        _activeId.value = c.id
        return c
    }

    fun setActive(id: String) { _activeId.value = id }
    fun active(): ResearchCase? = _cases.value.firstOrNull { it.id == _activeId.value }
    fun ensureActive(): ResearchCase = active() ?: create("Case ${_cases.value.size + 1}")

    fun addEntry(kind: String, target: String, title: String, body: String, urls: List<String> = emptyList()): ResearchCase {
        val c = ensureActive()
        val e = ResearchCase.Entry(UUID.randomUUID().toString(), System.currentTimeMillis(), kind, target.trim(), title, body, urls)
        val updated = c.withEntry(e)
        persist(_cases.value.map { if (it.id == c.id) updated else it })
        return updated
    }

    fun addTarget(t: String) {
        val c = ensureActive()
        persist(_cases.value.map { if (it.id == c.id) it.withTarget(t) else it })
    }

    fun removeEntry(caseId: String, entryId: String) =
        persist(_cases.value.map { if (it.id == caseId) it.withoutEntry(entryId) else it })

    fun delete(id: String) {
        persist(_cases.value.filterNot { it.id == id })
        if (_activeId.value == id) _activeId.value = _cases.value.lastOrNull()?.id
    }

    companion object {
        @Volatile private var instance: ResearchCaseStore? = null
        fun get(context: Context): ResearchCaseStore =
            instance ?: synchronized(this) { instance ?: ResearchCaseStore(context).also { instance = it } }
    }
}
