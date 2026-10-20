package com.victorious.browser.netid

import java.util.Locale

/** User-Agent presets. [DEFAULT] = keep the app's normal UA (no override). */
enum class UaPreset(val label: String, val platform: String?) {
    DEFAULT("Default (real)", null),
    CHROME_ANDROID("Chrome Android", "Linux armv8l"),
    CHROME_DESKTOP("Chrome Desktop (Windows)", "Win32"),
    SAFARI_IOS("Safari iOS", "iPhone"),
    GOOGLEBOT("Googlebot", null),
    CUSTOM("Custom", null);

    companion object {
        fun fromName(name: String?): UaPreset = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

object UaPresets {
    const val CHROME_ANDROID_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/129.0.0.0 Mobile Safari/537.36"
    const val CHROME_DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/129.0.0.0 Safari/537.36"
    const val SAFARI_IOS_UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6 like Mac OS X) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/17.6 Mobile/15E148 Safari/604.1"
    const val GOOGLEBOT_UA =
        "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

    /** Returns the UA to set, or null meaning "do not override". */
    fun resolve(preset: UaPreset, custom: String): String? = when (preset) {
        UaPreset.DEFAULT -> null
        UaPreset.CHROME_ANDROID -> CHROME_ANDROID_UA
        UaPreset.CHROME_DESKTOP -> CHROME_DESKTOP_UA
        UaPreset.SAFARI_IOS -> SAFARI_IOS_UA
        UaPreset.GOOGLEBOT -> GOOGLEBOT_UA
        UaPreset.CUSTOM -> custom.trim().replace(Regex("[\\r\\n]"), "").takeIf { it.isNotEmpty() }
    }
}

/** Per-surface fingerprint overrides. Every toggle off by default. */
data class FingerprintConfig(
    val uaPreset: UaPreset = UaPreset.DEFAULT,
    val customUa: String = "",
    val spoofPlatform: Boolean = false,
    val platform: String = "Win32",
    val spoofLanguages: Boolean = false,
    val languages: String = "en-US,en",
    val spoofHardwareConcurrency: Boolean = false,
    val hardwareConcurrency: Int = 8,
    val spoofScreen: Boolean = false,
    val screenWidth: Int = 1920,
    val screenHeight: Int = 1080,
    val spoofTimezone: Boolean = false,
    /** JS getTimezoneOffset() semantics: minutes, UTC − local (e.g. 300 for UTC-5). */
    val timezoneOffsetMinutes: Int = 0,
    val spoofWebGl: Boolean = false,
    val webGlVendor: String = "Google Inc. (Intel)",
    val webGlRenderer: String = "ANGLE (Intel, Intel(R) UHD Graphics 620 Direct3D11 vs_5_0 ps_5_0)",
) {
    val anyShimEnabled: Boolean
        get() = spoofPlatform || spoofLanguages || spoofHardwareConcurrency ||
            spoofScreen || spoofTimezone || spoofWebGl
}

object FingerprintShim {
    /** JSON-style string literal escaping safe for embedding in a JS source. */
    fun jsString(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '<' -> sb.append("\\u003c")
                c.code < 0x20 || c == '\u2028' || c == '\u2029' ->
                    sb.append(String.format(Locale.US, "\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    fun parseLanguages(raw: String): List<String> =
        raw.split(',').map { it.trim() }.filter { it.matches(Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")) }

    fun isValidTimezoneOffset(min: Int) = min in -840..720

    /** Generates the shim JS, or "" if no surface is enabled. */
    fun generate(c: FingerprintConfig): String {
        if (!c.anyShimEnabled) return ""
        val parts = mutableListOf<String>()
        fun nav(prop: String, valueJs: String) =
            "def(Navigator.prototype,'$prop',function(){return $valueJs;});"
        if (c.spoofPlatform) parts += nav("platform", jsString(c.platform))
        if (c.spoofLanguages) {
            val langs = parseLanguages(c.languages).ifEmpty { listOf("en-US") }
            val arr = langs.joinToString(",", "[", "]") { jsString(it) }
            parts += "var L=Object.freeze($arr);" + nav("languages", "L") + nav("language", "L[0]")
        }
        if (c.spoofHardwareConcurrency) {
            parts += nav("hardwareConcurrency", c.hardwareConcurrency.coerceIn(1, 128).toString())
        }
        if (c.spoofScreen) {
            val w = c.screenWidth.coerceIn(200, 10000); val h = c.screenHeight.coerceIn(200, 10000)
            parts += "def(Screen.prototype,'width',function(){return $w;});" +
                "def(Screen.prototype,'height',function(){return $h;});" +
                "def(Screen.prototype,'availWidth',function(){return $w;});" +
                "def(Screen.prototype,'availHeight',function(){return $h;});"
        }
        if (c.spoofTimezone && isValidTimezoneOffset(c.timezoneOffsetMinutes)) {
            parts += "Date.prototype.getTimezoneOffset=function(){return ${c.timezoneOffsetMinutes};};"
        }
        if (c.spoofWebGl) {
            val v = jsString(c.webGlVendor); val r = jsString(c.webGlRenderer)
            parts += """
['WebGLRenderingContext','WebGL2RenderingContext'].forEach(function(n){var C=window[n];if(!C)return;
var g=C.prototype.getParameter;C.prototype.getParameter=function(p){
if(p===37445)return $v;if(p===37446)return $r;return g.apply(this,arguments);};});
""".trim()
        }
        return "(function(){try{if(window.__vbFpShim)return;window.__vbFpShim=true;" +
            "function def(o,k,f){try{Object.defineProperty(o,k,{get:f,configurable:true});}catch(e){}}" +
            parts.joinToString("") + "}catch(e){}})();"
    }
}
