package com.victorious.browser.netid

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted Net ID / Spoof settings. Every feature OFF by default.
 * SharedPreferences keys are namespaced; passwords are stored in plaintext prefs
 * (device-local, same threat model as other app prefs — not for production secrets).
 */
class NetIdSettings private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _upstream = MutableStateFlow(loadUpstream())
    val upstream: StateFlow<UpstreamProxyConfig> = _upstream.asStateFlow()

    private val _geo = MutableStateFlow(loadGeo())
    val geo: StateFlow<GeoSpoofConfig> = _geo.asStateFlow()

    private val _fingerprint = MutableStateFlow(loadFingerprint())
    val fingerprint: StateFlow<FingerprintConfig> = _fingerprint.asStateFlow()

    fun setUpstream(config: UpstreamProxyConfig) {
        prefs.edit()
            .putBoolean(K_UP_ENABLED, config.enabled)
            .putString(K_UP_TYPE, config.type.name)
            .putString(K_UP_HOST, config.host)
            .putInt(K_UP_PORT, config.port)
            .putString(K_UP_USER, config.username)
            .putString(K_UP_PASS, config.password)
            .apply()
        _upstream.value = config
    }

    fun setGeo(config: GeoSpoofConfig) {
        prefs.edit()
            .putBoolean(K_GEO_ENABLED, config.enabled)
            .putString(K_GEO_LAT, config.latitude.toString())
            .putString(K_GEO_LNG, config.longitude.toString())
            .putString(K_GEO_ACC, config.accuracyMeters.toString())
            .apply()
        _geo.value = config
    }

    fun setFingerprint(config: FingerprintConfig) {
        prefs.edit()
            .putString(K_UA_PRESET, config.uaPreset.name)
            .putString(K_UA_CUSTOM, config.customUa)
            .putBoolean(K_FP_PLATFORM, config.spoofPlatform)
            .putString(K_FP_PLATFORM_V, config.platform)
            .putBoolean(K_FP_LANG, config.spoofLanguages)
            .putString(K_FP_LANG_V, config.languages)
            .putBoolean(K_FP_HW, config.spoofHardwareConcurrency)
            .putInt(K_FP_HW_V, config.hardwareConcurrency)
            .putBoolean(K_FP_SCREEN, config.spoofScreen)
            .putInt(K_FP_SW, config.screenWidth)
            .putInt(K_FP_SH, config.screenHeight)
            .putBoolean(K_FP_TZ, config.spoofTimezone)
            .putInt(K_FP_TZ_V, config.timezoneOffsetMinutes)
            .putBoolean(K_FP_WEBGL, config.spoofWebGl)
            .putString(K_FP_WGL_V, config.webGlVendor)
            .putString(K_FP_WGL_R, config.webGlRenderer)
            .apply()
        _fingerprint.value = config
    }

    private fun loadUpstream(): UpstreamProxyConfig {
        val type = try {
            UpstreamProxyConfig.Type.valueOf(prefs.getString(K_UP_TYPE, null) ?: "HTTP")
        } catch (_: Exception) { UpstreamProxyConfig.Type.HTTP }
        return UpstreamProxyConfig(
            enabled = prefs.getBoolean(K_UP_ENABLED, false),
            type = type,
            host = prefs.getString(K_UP_HOST, "") ?: "",
            port = prefs.getInt(K_UP_PORT, 0),
            username = prefs.getString(K_UP_USER, "") ?: "",
            password = prefs.getString(K_UP_PASS, "") ?: "",
        )
    }

    private fun loadGeo(): GeoSpoofConfig = GeoSpoofConfig(
        enabled = prefs.getBoolean(K_GEO_ENABLED, false),
        latitude = prefs.getString(K_GEO_LAT, "0")?.toDoubleOrNull() ?: 0.0,
        longitude = prefs.getString(K_GEO_LNG, "0")?.toDoubleOrNull() ?: 0.0,
        accuracyMeters = prefs.getString(K_GEO_ACC, "25")?.toDoubleOrNull() ?: 25.0,
    )

    private fun loadFingerprint(): FingerprintConfig = FingerprintConfig(
        uaPreset = UaPreset.fromName(prefs.getString(K_UA_PRESET, null)),
        customUa = prefs.getString(K_UA_CUSTOM, "") ?: "",
        spoofPlatform = prefs.getBoolean(K_FP_PLATFORM, false),
        platform = prefs.getString(K_FP_PLATFORM_V, "Win32") ?: "Win32",
        spoofLanguages = prefs.getBoolean(K_FP_LANG, false),
        languages = prefs.getString(K_FP_LANG_V, "en-US,en") ?: "en-US,en",
        spoofHardwareConcurrency = prefs.getBoolean(K_FP_HW, false),
        hardwareConcurrency = prefs.getInt(K_FP_HW_V, 8),
        spoofScreen = prefs.getBoolean(K_FP_SCREEN, false),
        screenWidth = prefs.getInt(K_FP_SW, 1920),
        screenHeight = prefs.getInt(K_FP_SH, 1080),
        spoofTimezone = prefs.getBoolean(K_FP_TZ, false),
        timezoneOffsetMinutes = prefs.getInt(K_FP_TZ_V, 0),
        spoofWebGl = prefs.getBoolean(K_FP_WEBGL, false),
        webGlVendor = prefs.getString(K_FP_WGL_V, FingerprintConfig().webGlVendor)
            ?: FingerprintConfig().webGlVendor,
        webGlRenderer = prefs.getString(K_FP_WGL_R, FingerprintConfig().webGlRenderer)
            ?: FingerprintConfig().webGlRenderer,
    )

    companion object {
        private const val PREFS = "netid_spoof"
        private const val K_UP_ENABLED = "up_enabled"
        private const val K_UP_TYPE = "up_type"
        private const val K_UP_HOST = "up_host"
        private const val K_UP_PORT = "up_port"
        private const val K_UP_USER = "up_user"
        private const val K_UP_PASS = "up_pass"
        private const val K_GEO_ENABLED = "geo_enabled"
        private const val K_GEO_LAT = "geo_lat"
        private const val K_GEO_LNG = "geo_lng"
        private const val K_GEO_ACC = "geo_acc"
        private const val K_UA_PRESET = "ua_preset"
        private const val K_UA_CUSTOM = "ua_custom"
        private const val K_FP_PLATFORM = "fp_platform"
        private const val K_FP_PLATFORM_V = "fp_platform_v"
        private const val K_FP_LANG = "fp_lang"
        private const val K_FP_LANG_V = "fp_lang_v"
        private const val K_FP_HW = "fp_hw"
        private const val K_FP_HW_V = "fp_hw_v"
        private const val K_FP_SCREEN = "fp_screen"
        private const val K_FP_SW = "fp_sw"
        private const val K_FP_SH = "fp_sh"
        private const val K_FP_TZ = "fp_tz"
        private const val K_FP_TZ_V = "fp_tz_v"
        private const val K_FP_WEBGL = "fp_webgl"
        private const val K_FP_WGL_V = "fp_wgl_v"
        private const val K_FP_WGL_R = "fp_wgl_r"

        @Volatile private var instance: NetIdSettings? = null

        fun get(context: Context): NetIdSettings =
            instance ?: synchronized(this) {
                instance ?: NetIdSettings(context).also { instance = it }
            }
    }
}
