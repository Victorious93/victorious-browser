package com.victorious.browser.osint.research

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream

/** Android glue: decode bounds, EXIF and perceptual hash for an image held in memory. */
object ImageInspector {

    fun readUri(context: Context, uri: Uri, maxBytes: Int = ResearchNet.MAX_IMAGE_BYTES): Result<ByteArray> = runCatching {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open the file" }
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                require(out.size() <= maxBytes) { "Image larger than ${maxBytes / (1024 * 1024)} MB" }
            }
            out.toByteArray()
        }
    }

    fun inspect(bytes: ByteArray): ImageMetadata.Summary {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth.takeIf { it > 0 }
        val h = bounds.outHeight.takeIf { it > 0 }
        val exif = runCatching {
            val ei = ExifInterface(ByteArrayInputStream(bytes))
            ImageMetadata.cleanExif(ImageMetadata.EXIF_TAGS.associateWith { ei.getAttribute(it) })
        }.getOrDefault(emptyMap())
        val gps = ImageMetadata.gpsFrom(exif)
        return ImageMetadata.Summary(
            width = w, height = h,
            mime = bounds.outMimeType ?: ImageMetadata.sniffMime(bytes),
            sizeBytes = bytes.size.toLong(),
            md5 = ImageMetadata.md5(bytes),
            sha256 = ImageMetadata.sha256(bytes),
            dHash = if (w != null && h != null) dHash(bytes, w, h) else null,
            exif = exif,
            lat = gps?.first, lon = gps?.second,
        )
    }

    private fun dHash(bytes: ByteArray, w: Int, h: Int): String? = runCatching {
        // Subsample on decode so huge photos don't blow memory, then scale to 9×8.
        var sample = 1
        while (w / (sample * 2) >= 64 && h / (sample * 2) >= 64) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val small = Bitmap.createScaledBitmap(bmp, 9, 8, true)
        val px = IntArray(72)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        if (small !== bmp) small.recycle()
        bmp.recycle()
        ImageMetadata.dHash(IntArray(72) { ImageMetadata.luma(px[it]) })
    }.getOrNull()
}
