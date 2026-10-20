package com.victorious.browser.fuzz

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manager for fuzzing wordlists (SecLists-style, user-supplied).
 *
 * Don's no-huge-downloads rule: this app does NOT bundle or auto-download the
 * full SecLists. It ships a few SMALL curated starter lists under
 * assets/fuzz/wordlists/ (common dirs, common files, top-100 params) whose
 * lineage is credited to SecLists (MIT) in NOTICE, and lets the user import
 * their own lists from a file, pasted text, or a URL they provide.
 *
 * Imported lists live in the app files dir (filesDir/wordlists). An import from
 * URL is capped at [MAX_IMPORT_BYTES] to keep "no large downloads" true even for
 * user-provided URLs.
 */
class WordlistManager(context: Context) {
    private val appContext = context.applicationContext
    private val dir: File = File(appContext.filesDir, "wordlists").apply { mkdirs() }
    private val assets = appContext.assets

    data class Entry(
        val name: String,
        val bundled: Boolean,
        val count: Int,
        val sizeBytes: Long,
    )

    /** Names of bundled starter lists (asset files). */
    fun bundledNames(): List<String> =
        runCatching { assets.list(ASSET_DIR)?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .filter { it.endsWith(".txt") }
            .sorted()

    fun importedFiles(): List<File> =
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    /** Read the raw text of a list (bundled or imported). */
    fun read(name: String, bundled: Boolean): String =
        if (bundled) {
            assets.open("$ASSET_DIR/$name").bufferedReader().use { it.readText() }
        } else {
            File(dir, name).readText()
        }

    fun words(name: String, bundled: Boolean): List<String> =
        WordlistParser.lines(read(name, bundled))

    fun list(): List<Entry> {
        val bundled = bundledNames().map { n ->
            val raw = runCatching { read(n, true) }.getOrDefault("")
            Entry(n, true, WordlistParser.count(raw), raw.toByteArray(Charsets.UTF_8).size.toLong())
        }
        val imported = importedFiles().map { f ->
            val raw = runCatching { f.readText() }.getOrDefault("")
            Entry(f.name, false, WordlistParser.count(raw), f.length())
        }
        return bundled + imported
    }

    fun preview(name: String, bundled: Boolean, max: Int = 50): List<String> =
        WordlistParser.preview(read(name, bundled), max)

    /** Save pasted / file text as a new imported list. Returns the stored file name. */
    fun importText(name: String, content: String): String {
        val fileName = ensureTxt(WordlistParser.safeName(name))
        File(dir, fileName).writeText(content)
        return fileName
    }

    /** Import from a user-provided URL (capped). Returns the stored file name. */
    fun importUrl(name: String, urlStr: String): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VictoriousBrowser-WordlistImport")
        }
        try {
            val text = conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                val sb = StringBuilder()
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_IMPORT_BYTES) {
                        throw IllegalStateException(
                            "Wordlist exceeds ${MAX_IMPORT_BYTES / 1024} KB import cap " +
                                "(no large downloads). Import a smaller list or split it.",
                        )
                    }
                    sb.append(String(buf, 0, n, Charsets.UTF_8))
                }
                sb.toString()
            }
            return importText(name, text)
        } finally {
            conn.disconnect()
        }
    }

    /** Delete an imported list (bundled lists cannot be deleted). */
    fun delete(name: String): Boolean {
        val f = File(dir, name)
        return if (f.exists() && f.isFile) f.delete() else false
    }

    private fun ensureTxt(stem: String): String =
        if (stem.endsWith(".txt")) stem else "$stem.txt"

    companion object {
        private const val ASSET_DIR = "fuzz/wordlists"
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024 // 5 MB cap honoring no-huge-downloads
    }
}
