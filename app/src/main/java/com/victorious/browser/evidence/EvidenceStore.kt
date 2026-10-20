package com.victorious.browser.evidence

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * On-device store for evidence cases. One folder per case under
 * filesDir/evidence/<caseFolder>/, with an index.json and screenshot PNGs.
 *
 * The "active case" is where the browser toolbar screenshot action and quick
 * captures land; it is remembered in SharedPreferences.
 *
 * Secret-stripping for REQUEST evidence is done in [EvidenceSanitizer] (reusing
 * the fleet SecretStripper) BEFORE anything is written — no secrets hit disk.
 */
class EvidenceStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val root = File(appContext.filesDir, "evidence").apply { mkdirs() }

    private val _cases = MutableStateFlow(loadAllCases())
    val cases: StateFlow<List<EvidenceCase>> = _cases.asStateFlow()

    private val _activeCaseId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeCaseId: StateFlow<String?> = _activeCaseId.asStateFlow()

    // --- case folders ---------------------------------------------------------

    private fun caseDir(case: EvidenceCase): File =
        File(root, EvidenceFilenames.caseFolderName(case.createdAt, case.name))

    private fun findCaseDirById(id: String): File? =
        root.listFiles()?.firstOrNull { dir ->
            val idx = File(dir, EvidenceIndex.INDEX_FILE)
            idx.exists() && runCatching { EvidenceIndex.fromJson(idx.readText()).id == id }.getOrDefault(false)
        }

    private fun loadAllCases(): List<EvidenceCase> {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val idx = File(dir, EvidenceIndex.INDEX_FILE)
            if (!idx.exists()) return@mapNotNull null
            runCatching { EvidenceIndex.fromJson(idx.readText()) }.getOrNull()
        }.sortedByDescending { it.createdAt }
    }

    private fun persist(case: EvidenceCase) {
        val dir = caseDir(case)
        dir.mkdirs()
        File(dir, EvidenceIndex.INDEX_FILE).writeText(EvidenceIndex.toJson(case))
        _cases.value = loadAllCases()
    }

    private fun caseById(id: String): EvidenceCase? = _cases.value.firstOrNull { it.id == id }

    // --- public API -----------------------------------------------------------

    fun createCase(name: String): EvidenceCase {
        val case = EvidenceCase(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "case" },
            createdAt = System.currentTimeMillis(),
        )
        persist(case)
        setActive(case.id)
        return case
    }

    fun setActive(id: String?) {
        _activeCaseId.value = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    /** Ensure there is an active case, creating a default one if needed. */
    fun ensureActiveCase(): EvidenceCase {
        val id = _activeCaseId.value
        val existing = id?.let { caseById(it) }
        return existing ?: createCase("Case ${_cases.value.size + 1}")
    }

    fun deleteCase(id: String) {
        findCaseDirById(id)?.deleteRecursively()
        if (_activeCaseId.value == id) setActive(null)
        _cases.value = loadAllCases()
    }

    private fun addItem(caseId: String, item: EvidenceItem): EvidenceCase? {
        val case = caseById(caseId) ?: return null
        val updated = case.withItem(item)
        persist(updated)
        return updated
    }

    fun addNote(caseId: String, host: String, title: String, note: String): EvidenceCase? =
        addItem(
            caseId,
            EvidenceItem(
                id = UUID.randomUUID().toString(),
                type = EvidenceType.NOTE,
                timestamp = System.currentTimeMillis(),
                host = host,
                title = title.ifBlank { "note" },
                note = note,
            ),
        )

    fun addFinding(caseId: String, host: String, title: String, detail: String): EvidenceCase? =
        addItem(
            caseId,
            EvidenceItem(
                id = UUID.randomUUID().toString(),
                type = EvidenceType.FINDING,
                timestamp = System.currentTimeMillis(),
                host = host,
                title = title.ifBlank { "finding" },
                data = detail,
            ),
        )

    /** Save a secret-stripped request/response exchange as evidence. */
    fun addRequest(
        caseId: String,
        method: String,
        url: String,
        requestHeaders: Map<String, String>,
        responseHeaders: Map<String, String>,
        statusCode: Int?,
        title: String = "",
    ): EvidenceCase? {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        val data = EvidenceSanitizer.sanitizedExchange(
            method, url, requestHeaders, responseHeaders, statusCode,
        )
        return addItem(
            caseId,
            EvidenceItem(
                id = UUID.randomUUID().toString(),
                type = EvidenceType.REQUEST,
                timestamp = System.currentTimeMillis(),
                host = host,
                title = title.ifBlank { "$method $host" },
                data = data,
            ),
        )
    }

    /** Save a PNG screenshot bitmap into the case folder. Returns updated case. */
    fun addScreenshot(
        caseId: String,
        bitmap: Bitmap,
        host: String,
        title: String = "screenshot",
        note: String? = null,
    ): EvidenceCase? {
        val case = caseById(caseId) ?: return null
        val ts = System.currentTimeMillis()
        val fileName = EvidenceFilenames.screenshotName(ts, host)
        val dir = caseDir(case).apply { mkdirs() }
        val bytes = ByteArrayOutputStream().use { bos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos)
            bos.toByteArray()
        }
        File(dir, fileName).writeBytes(bytes)
        return addItem(
            caseId,
            EvidenceItem(
                id = UUID.randomUUID().toString(),
                type = EvidenceType.SCREENSHOT,
                timestamp = ts,
                host = host,
                title = title,
                fileRef = fileName,
                note = note,
            ),
        )
    }

    fun caseFolderFor(case: EvidenceCase): File = caseDir(case)

    companion object {
        private const val PREFS = "evidence_store"
        private const val KEY_ACTIVE = "active_case_id"

        @Volatile private var instance: EvidenceStore? = null

        fun get(context: Context): EvidenceStore =
            instance ?: synchronized(this) {
                instance ?: EvidenceStore(context).also { instance = it }
            }
    }
}
