package com.victorious.browser.evidence

import android.content.Context
import com.victorious.browser.export.MediaStoreDownloads
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Exports an evidence case as a ZIP (screenshots + generated Markdown & HTML
 * report + the raw index.json) to the user's Downloads via [MediaStoreDownloads].
 *
 * ZIP built with java.util.zip (no new deps).
 */
object EvidenceExporter {

    private const val SUBDIR = "VictoriousBrowser"

    data class Result(val location: String?, val entryCount: Int)

    /** Build the ZIP bytes for [case], reading screenshots from [caseDir]. */
    fun buildZipBytes(case: EvidenceCase, caseDir: File): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("report.md", EvidenceReport.markdown(case).toByteArray(Charsets.UTF_8))
            put("report.html", EvidenceReport.html(case).toByteArray(Charsets.UTF_8))
            put(EvidenceIndex.INDEX_FILE, EvidenceIndex.toJson(case).toByteArray(Charsets.UTF_8))
            // screenshots referenced by items
            case.items.mapNotNull { it.fileRef }.distinct().forEach { ref ->
                val f = File(caseDir, ref)
                if (f.exists() && f.isFile) {
                    put("screenshots/$ref", f.readBytes())
                }
            }
        }
        return bos.toByteArray()
    }

    fun export(context: Context, case: EvidenceCase, caseDir: File): Result {
        val bytes = buildZipBytes(case, caseDir)
        val name = EvidenceFilenames.caseZipName(case.name, System.currentTimeMillis())
        val loc = MediaStoreDownloads.write(
            context,
            displayName = name,
            mimeType = "application/zip",
            bytes = bytes,
            subDir = SUBDIR,
        )
        val shots = case.items.count { it.fileRef != null }
        return Result(loc, shots + 3)
    }
}
