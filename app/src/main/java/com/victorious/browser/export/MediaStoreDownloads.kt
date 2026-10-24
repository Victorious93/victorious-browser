package com.victorious.browser.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Minimal helper to write a file into the public Downloads collection.
 *
 * On Android 10+ (API 29) uses MediaStore (scoped storage, no permission needed).
 * On API 26–28 it falls back to writing directly into the public Downloads dir;
 * on those versions WRITE_EXTERNAL_STORAGE is declared (maxSdkVersion=28).
 *
 * Returns a human-readable location string, or null on failure.
 */
object MediaStoreDownloads {

    /** Write [bytes] to Downloads/[subDir]/[displayName] with the given MIME type. */
    fun write(
        context: Context,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        subDir: String? = null,
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(context, displayName, mimeType, bytes, subDir)
        } else {
            writeLegacy(displayName, bytes, subDir)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeViaMediaStore(
        context: Context,
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
        subDir: String?,
    ): String? {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relPath = if (subDir.isNullOrBlank()) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            Environment.DIRECTORY_DOWNLOADS + "/" + subDir
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relPath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            "$relPath/$displayName"
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun writeLegacy(displayName: String, bytes: ByteArray, subDir: String?): String? {
        return try {
            @Suppress("DEPRECATION")
            val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = if (subDir.isNullOrBlank()) base else File(base, subDir)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, displayName)
            file.writeBytes(bytes)
            file.absolutePath
        } catch (t: Throwable) {
            null
        }
    }
}
