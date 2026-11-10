package com.victorious.browser.browser

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URI

/** Per-site capabilities the user can control from Site settings. */
enum class SitePermission(val key: String, val label: String) {
    CAMERA("camera", "Camera"),
    MIC("mic", "Microphone"),
    LOCATION("location", "Location"),
    POPUPS("popups", "Pop-ups & redirects"),
    JAVASCRIPT("javascript", "JavaScript"),
    ;

    companion object {
        fun fromKey(key: String?): SitePermission? = entries.firstOrNull { it.key == key }
    }
}

/** Stored decision for one (site, permission). Absence of a row means [ASK]. */
enum class PermissionDecision { ASK, ALLOW, BLOCK }

/**
 * Pure logic for per-site permissions: host keys, WebView resource mapping, the
 * default-when-ASK resolution, and JSON (de)serialisation. Android-free so every
 * rule is unit-tested in [SitePermissionsTest].
 */
object SitePermissionLogic {
    // WebChromeClient.PermissionRequest resource identifiers (stable platform strings).
    const val RESOURCE_VIDEO = "android.webkit.resource.VIDEO_CAPTURE"
    const val RESOURCE_AUDIO = "android.webkit.resource.AUDIO_CAPTURE"

    /** Lowercased host of [url], the key we store decisions under. Null for non-web URLs. */
    fun hostKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching { URI(url.trim()).host }.getOrNull()?.lowercase()?.takeIf { it.isNotBlank() }
    }

    /** WebView permission-request resources → the [SitePermission]s they map to. */
    fun permissionsForResources(resources: Array<String>?): Set<SitePermission> {
        if (resources == null) return emptySet()
        val out = linkedSetOf<SitePermission>()
        for (r in resources) when (r) {
            RESOURCE_VIDEO -> out += SitePermission.CAMERA
            RESOURCE_AUDIO -> out += SitePermission.MIC
        }
        return out
    }

    /** The subset of [resources] to grant given each one's resolved decision (ALLOW only). */
    fun grantedResources(resources: Array<String>?, decisionOf: (SitePermission) -> PermissionDecision): Array<String> {
        if (resources == null) return emptyArray()
        return resources.filter { r ->
            when (r) {
                RESOURCE_VIDEO -> decisionOf(SitePermission.CAMERA) == PermissionDecision.ALLOW
                RESOURCE_AUDIO -> decisionOf(SitePermission.MIC) == PermissionDecision.ALLOW
                else -> false
            }
        }.toTypedArray()
    }

    /**
     * Effective behaviour when a stored decision is [ASK] (i.e. nothing set).
     * JavaScript defaults ON (ALLOW); everything else defaults to "ask / block"
     * until the user allows it, so an unset capability-prompt stays ASK.
     */
    fun effective(permission: SitePermission, stored: PermissionDecision): PermissionDecision {
        if (stored != PermissionDecision.ASK) return stored
        return if (permission == SitePermission.JAVASCRIPT) PermissionDecision.ALLOW else PermissionDecision.ASK
    }

    // ---- JSON: { host: { permKey: "ALLOW"|"BLOCK" } } (ASK rows are never written) ----

    fun encode(map: Map<String, Map<SitePermission, PermissionDecision>>): String {
        val root = JSONObject()
        for ((host, perms) in map) {
            val obj = JSONObject()
            for ((perm, dec) in perms) if (dec != PermissionDecision.ASK) obj.put(perm.key, dec.name)
            if (obj.length() > 0) root.put(host, obj)
        }
        return root.toString()
    }

    fun decode(raw: String?): Map<String, Map<SitePermission, PermissionDecision>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            val out = linkedMapOf<String, MutableMap<SitePermission, PermissionDecision>>()
            for (host in root.keys()) {
                val obj = root.optJSONObject(host) ?: continue
                val perms = linkedMapOf<SitePermission, PermissionDecision>()
                for (k in obj.keys()) {
                    val perm = SitePermission.fromKey(k) ?: continue
                    val dec = runCatching { PermissionDecision.valueOf(obj.optString(k)) }.getOrNull() ?: continue
                    if (dec != PermissionDecision.ASK) perms[perm] = dec
                }
                if (perms.isNotEmpty()) out[host] = perms
            }
            out
        }.getOrDefault(emptyMap())
    }

    /** Return a new map with ([host],[permission]) set to [decision] (ASK removes the row). */
    fun withDecision(
        map: Map<String, Map<SitePermission, PermissionDecision>>,
        host: String,
        permission: SitePermission,
        decision: PermissionDecision,
    ): Map<String, Map<SitePermission, PermissionDecision>> {
        val out = map.mapValues { it.value.toMutableMap() }.toMutableMap()
        val perms = out.getOrPut(host) { mutableMapOf() }
        if (decision == PermissionDecision.ASK) perms.remove(permission) else perms[permission] = decision
        if (perms.isEmpty()) out.remove(host)
        return out
    }

    /** Return a new map with every stored decision for [host] removed. */
    fun cleared(
        map: Map<String, Map<SitePermission, PermissionDecision>>,
        host: String,
    ): Map<String, Map<SitePermission, PermissionDecision>> = map.filterKeys { it != host }
}

/**
 * SharedPreferences-backed per-site permission store (persistent, non-private tabs).
 * Private-tab grants are never routed here — the UI keeps those in a transient
 * in-memory map so they vanish with the session.
 */
class SitePermissionStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _sites = MutableStateFlow(SitePermissionLogic.decode(prefs.getString(KEY, null)))
    val sites: StateFlow<Map<String, Map<SitePermission, PermissionDecision>>> = _sites.asStateFlow()

    fun decision(host: String?, permission: SitePermission): PermissionDecision {
        val h = host ?: return PermissionDecision.ASK
        return _sites.value[h]?.get(permission) ?: PermissionDecision.ASK
    }

    /** Resolved behaviour (applies JS-default etc.) for ([host],[permission]). */
    fun effective(host: String?, permission: SitePermission): PermissionDecision =
        SitePermissionLogic.effective(permission, decision(host, permission))

    fun set(host: String, permission: SitePermission, decision: PermissionDecision) {
        _sites.value = SitePermissionLogic.withDecision(_sites.value, host, permission, decision)
        persist()
    }

    fun clearSite(host: String) {
        _sites.value = SitePermissionLogic.cleared(_sites.value, host)
        persist()
    }

    private fun persist() {
        prefs.edit().putString(KEY, SitePermissionLogic.encode(_sites.value)).apply()
    }

    companion object {
        private const val PREFS = "site_permissions"
        private const val KEY = "sites_json"

        @Volatile private var instance: SitePermissionStore? = null
        fun get(context: Context): SitePermissionStore =
            instance ?: synchronized(this) { instance ?: SitePermissionStore(context).also { instance = it } }
    }
}
