package com.victorious.browser.osint.research

import java.security.MessageDigest
import java.util.Locale

/** Pure image-metadata helpers: hashes, perceptual hash, EXIF formatting. */
object ImageMetadata {

    fun md5(bytes: ByteArray): String = EmailChecks.hex(MessageDigest.getInstance("MD5").digest(bytes))
    fun sha256(bytes: ByteArray): String = EmailChecks.hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    /**
     * Difference hash (dHash, 64-bit): [gray] is a 9×8 row-major luminance grid
     * (the image scaled down to 9 wide × 8 tall). Bit = left pixel brighter than right.
     * Robust to resizing/re-compression; compare two with [hamming] (≤10 ≈ same picture).
     */
    fun dHash(gray: IntArray): String {
        require(gray.size == 72) { "dHash needs a 9x8 grid (72 values)" }
        var h = 0UL
        for (y in 0 until 8) for (x in 0 until 8) {
            h = h shl 1
            if (gray[y * 9 + x] > gray[y * 9 + x + 1]) h = h or 1UL
        }
        return h.toString(16).padStart(16, '0')
    }

    /** Average hash (aHash, 64-bit) over an 8×8 luminance grid. */
    fun aHash(gray: IntArray): String {
        require(gray.size == 64) { "aHash needs an 8x8 grid (64 values)" }
        val avg = gray.sum() / 64.0
        var h = 0UL
        for (v in gray) { h = h shl 1; if (v > avg) h = h or 1UL }
        return h.toString(16).padStart(16, '0')
    }

    fun hamming(a: String, b: String): Int =
        (a.toULong(16) xor b.toULong(16)).countOneBits()

    /** ITU-R BT.601 luma from ARGB. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }

    /**
     * EXIF GPS rational triple (`"40/1,26/1,4630/100"`) + ref (`N/S/E/W`) → signed decimal degrees.
     * Returns null for malformed input.
     */
    fun dmsToDecimal(dms: String?, ref: String?): Double? {
        val parts = dms?.split(',')?.map { it.trim() } ?: return null
        if (parts.size != 3) return null
        val nums = parts.map { p ->
            val f = p.split('/')
            val n = f[0].toDoubleOrNull() ?: return null
            val d = if (f.size > 1) f[1].toDoubleOrNull() ?: return null else 1.0
            if (d == 0.0) return null
            n / d
        }
        val v = nums[0] + nums[1] / 60.0 + nums[2] / 3600.0
        return when (ref?.trim()?.uppercase()) {
            "S", "W" -> -v
            else -> v
        }
    }

    fun mapsUrl(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=16/%.6f/%.6f", lat, lon, lat, lon)

    data class Summary(
        val width: Int?,
        val height: Int?,
        val mime: String?,
        val sizeBytes: Long,
        val md5: String,
        val sha256: String,
        val dHash: String?,
        val exif: Map<String, String>,
        val lat: Double?,
        val lon: Double?,
    ) {
        val hasGps get() = lat != null && lon != null

        fun toText(source: String): String = buildString {
            appendLine("Image: $source")
            if (width != null && height != null) appendLine("Dimensions: ${width}×$height")
            mime?.let { appendLine("Type: $it") }
            appendLine("Size: ${humanSize(sizeBytes)} ($sizeBytes bytes)")
            appendLine("MD5: $md5")
            appendLine("SHA-256: $sha256")
            dHash?.let { appendLine("dHash: $it") }
            if (hasGps) appendLine(String.format(Locale.US, "GPS: %.6f, %.6f  %s", lat, lon, mapsUrl(lat!!, lon!!)))
            if (exif.isEmpty()) appendLine("EXIF: none found") else {
                appendLine("EXIF:")
                exif.forEach { (k, v) -> appendLine("  $k: $v") }
            }
        }.trimEnd()
    }

    /** EXIF tags we surface, in display order (names match androidx ExifInterface TAG_* values). */
    val EXIF_TAGS: List<String> = listOf(
        "Make", "Model", "LensModel", "Software", "DateTime", "DateTimeOriginal", "DateTimeDigitized",
        "OffsetTimeOriginal", "Artist", "Copyright", "ImageDescription", "Orientation",
        "ExposureTime", "FNumber", "ISOSpeedRatings", "FocalLength", "Flash",
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude",
        "GPSAltitudeRef", "GPSTimeStamp", "GPSDateStamp", "ImageUniqueID", "BodySerialNumber", "CameraOwnerName",
    )

    /** Keep only known, non-blank tags in [EXIF_TAGS] order; trim values. */
    fun cleanExif(raw: Map<String, String?>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (t in EXIF_TAGS) raw[t]?.trim()?.takeIf { it.isNotEmpty() && it != "0/0" }?.let { out[t] = it.take(200) }
        return out
    }

    fun gpsFrom(exif: Map<String, String>): Pair<Double, Double>? {
        val lat = dmsToDecimal(exif["GPSLatitude"], exif["GPSLatitudeRef"]) ?: return null
        val lon = dmsToDecimal(exif["GPSLongitude"], exif["GPSLongitudeRef"]) ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        return lat to lon
    }

    /** Sniff a MIME type from magic bytes. */
    fun sniffMime(b: ByteArray): String? = when {
        b.size >= 3 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte() -> "image/jpeg"
        b.size >= 8 && b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() && b[2] == 'N'.code.toByte() && b[3] == 'G'.code.toByte() -> "image/png"
        b.size >= 6 && String(b, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" } -> "image/gif"
        b.size >= 12 && String(b, 0, 4, Charsets.US_ASCII) == "RIFF" && String(b, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        b.size >= 12 && String(b, 4, 4, Charsets.US_ASCII) == "ftyp" &&
            String(b, 8, 4, Charsets.US_ASCII).let { it.startsWith("hei") || it.startsWith("mif") || it == "avif" } -> "image/heif"
        else -> null
    }
}
