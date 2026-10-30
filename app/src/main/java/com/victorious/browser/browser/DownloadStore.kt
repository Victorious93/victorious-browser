package com.victorious.browser.browser

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

/** A download handed to the system [DownloadManager]. */
data class DownloadRecord(
    val downloadId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val startedAt: Long,
)

/** Live status read back from DownloadManager (not persisted). */
enum class DownloadState { PENDING, RUNNING, PAUSED, DONE, FAILED, MISSING }

data class DownloadStatus(val state: DownloadState, val bytesSoFar: Long, val totalBytes: Long)

/** Pure helpers (unit-tested in DownloadLogicTest). */
internal object DownloadLogic {
    const val MAX_RECORDS = 200

    private val UNSAFE = Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]")

    /**
     * Best-effort file name: Content-Disposition `filename*=` (RFC 5987) then
     * `filename=`, else the last URL path segment, else "download". Path
     * separators and control chars are stripped so a server can't traverse
     * out of the Downloads directory. Adds an extension for a few common MIME
     * types when the name has none.
     */
    fun guessFileName(url: String, contentDisposition: String?, mimeType: String?): String {
        var name: String? = null
        if (!contentDisposition.isNullOrBlank()) {
            Regex("filename\\*\\s*=\\s*(?:[\\w-]+)''([^;]+)", RegexOption.IGNORE_CASE)
                .find(contentDisposition)?.let {
                    name = runCatching { URLDecoder.decode(it.groupValues[1].trim(), "UTF-8") }.getOrNull()
                }
            if (name.isNullOrBlank()) {
                Regex("filename\\s*=\\s*(\"([^\"]*)\"|[^;]+)", RegexOption.IGNORE_CASE)
                    .find(contentDisposition)?.let { m ->
                        name = m.groupValues[2].ifEmpty { m.groupValues[1] }.trim().trim('"')
                    }
            }
        }
        if (name.isNullOrBlank()) {
            val path = runCatching { URI(url).rawPath }.getOrNull().orEmpty()
            name = runCatching { URLDecoder.decode(path.substringAfterLast('/'), "UTF-8") }.getOrNull()
        }
        var clean = (name ?: "").substringAfterLast('/').substringAfterLast('\\')
            .replace(UNSAFE, "_").trim().trimStart('.')
        if (clean.isBlank()) clean = "download"
        if (!clean.contains('.')) {
            extensionFor(mimeType)?.let { clean = "$clean.$it" }
        }
        return clean.take(120)
    }

    fun extensionFor(mime: String?): String? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        "application/json" -> "json"
        "text/plain" -> "txt"
        "text/html" -> "html"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "application/vnd.android.package-archive" -> "apk"
        else -> null
    }

    fun add(list: List<DownloadRecord>, rec: DownloadRecord, max: Int = MAX_RECORDS) =
        (listOf(rec) + list.filterNot { it.downloadId == rec.downloadId }).take(max)

    fun encode(list: List<DownloadRecord>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.downloadId).put("url", it.url).put("name", it.fileName)
                    .put("mime", it.mimeType).put("t", it.startedAt),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<DownloadRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (!o.has("id")) continue
                    add(DownloadRecord(o.getLong("id"), o.optString("url"), o.optString("name", "download"), o.optString("mime"), o.optLong("t")))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun formatBytes(b: Long): String = when {
        b < 0 -> "?"
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", b / (1024.0 * 1024))
        else -> String.format(java.util.Locale.US, "%.2f GB", b / (1024.0 * 1024 * 1024))
    }
}

/**
 * Downloads go through the system [DownloadManager] (system notification, survives
 * app exit). This store only remembers which downloads the browser started so the
 * in-app list can show them and query their live status.
 *
 * Note: DownloadManager fetches over its own HTTP stack — it does NOT go through the
 * WebView proxy override, so downloads are not captured in proxy history.
 */
class DownloadStore private constructor(private val context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("browser_downloads", Context.MODE_PRIVATE)
    private val _records = MutableStateFlow(DownloadLogic.decode(prefs.getString(KEY, null)))
    val records: StateFlow<List<DownloadRecord>> = _records.asStateFlow()

    private val dm: DownloadManager?
        get() = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager

    /** Enqueue [url]; returns the saved file name, or throws on failure. */
    fun enqueue(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?): String {
        val name = DownloadLogic.guessFileName(url, contentDisposition, mimeType)
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(name)
            setDescription(Uri.parse(url).host ?: url)
            if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            if (!userAgent.isNullOrBlank()) addRequestHeader("User-Agent", userAgent)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            } else {
                // App-specific dir: no WRITE_EXTERNAL_STORAGE runtime prompt needed.
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
            }
        }
        val manager = dm ?: error("DownloadManager unavailable")
        val id = manager.enqueue(req)
        set(DownloadLogic.add(_records.value, DownloadRecord(id, url, name, mimeType.orEmpty(), System.currentTimeMillis())))
        return name
    }

    fun status(id: Long): DownloadStatus {
        val manager = dm ?: return DownloadStatus(DownloadState.MISSING, 0, -1)
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
                if (!c.moveToFirst()) return DownloadStatus(DownloadState.MISSING, 0, -1)
                val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val so = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val tot = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val state = when (st) {
                    DownloadManager.STATUS_PENDING -> DownloadState.PENDING
                    DownloadManager.STATUS_RUNNING -> DownloadState.RUNNING
                    DownloadManager.STATUS_PAUSED -> DownloadState.PAUSED
                    DownloadManager.STATUS_SUCCESSFUL -> DownloadState.DONE
                    else -> DownloadState.FAILED
                }
                DownloadStatus(state, so, tot)
            }
        }.getOrDefault(DownloadStatus(DownloadState.MISSING, 0, -1))
    }

    /** Content URI for a finished download (for ACTION_VIEW), or null. */
    fun fileUri(id: Long): Uri? = runCatching { dm?.getUriForDownloadedFile(id) }.getOrNull()

    /** Remove from the list; [deleteFile] also cancels/deletes it in DownloadManager. */
    fun remove(id: Long, deleteFile: Boolean) {
        if (deleteFile) runCatching { dm?.remove(id) }
        set(_records.value.filterNot { it.downloadId == id })
    }

    fun clearList() = set(emptyList())

    private fun set(next: List<DownloadRecord>) {
        _records.value = next
        prefs.edit().putString(KEY, DownloadLogic.encode(next)).apply()
    }

    companion object {
        private const val KEY = "downloads_json"
        @Volatile private var instance: DownloadStore? = null
        fun get(context: Context): DownloadStore =
            instance ?: synchronized(this) { instance ?: DownloadStore(context.applicationContext).also { instance = it } }
    }
}
