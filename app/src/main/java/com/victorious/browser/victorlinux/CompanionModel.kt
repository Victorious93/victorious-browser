package com.victorious.browser.victorlinux

import org.json.JSONArray
import org.json.JSONObject

/** One saved VictorLinux PC. The API key is NOT part of this class: it lives in the Keystore, bound to [origin]. */
data class VictorProfile(
    val id: String,
    val name: String,
    val origin: String,
    /** Owner-confirmed SHA-256 of the PC's TLS certificate, for self-signed gateways. Null = normal CA validation. */
    val pinnedCertSha256: String? = null,
    /** Pinned on first successful pairing; a different id at the same address means a different PC. */
    val pcId: String? = null,
    val lastModel: String? = null,
    val tunnel: Boolean = false,
)

data class ModelFit(val name: String, val fit: String, val context: Int?, val verified: String, val sizeBytes: Long?)

data class CompanionReport(
    val schema: Int,
    val pcId: String,
    val pcName: String,
    val ramTotal: Long?,
    val ramAvailable: Long?,
    val gpus: List<String>,
    val backend: String?,
    val memoryPool: String?,
    val memoryBudget: Long?,
    val running: Boolean,
    val model: String?,
    val context: Int?,
    val gpuLayers: Int?,
    val generationCheck: String?,
    val tls: Boolean,
    val tlsFingerprint: String?,
    val models: List<ModelFit>,
) {
    companion object {
        const val SUPPORTED_SCHEMA = 1

        fun parse(json: String): CompanionReport {
            val o = JSONObject(json)
            val schema = o.optInt("schema", -1)
            require(schema == SUPPORTED_SCHEMA) { "unsupported companion schema $schema" }
            val pc = o.getJSONObject("pc")
            val hw = o.optJSONObject("hardware") ?: JSONObject()
            val rt = o.optJSONObject("runtime") ?: JSONObject()
            val tr = o.optJSONObject("transport") ?: JSONObject()
            val gpus = hw.optJSONArray("gpus") ?: JSONArray()
            val models = o.optJSONArray("models") ?: JSONArray()
            fun JSONObject.long(k: String): Long? = if (has(k) && !isNull(k)) optLong(k) else null
            fun JSONObject.int(k: String): Int? = if (has(k) && !isNull(k)) optInt(k) else null
            fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) optString(k) else null
            return CompanionReport(
                schema = schema, pcId = pc.getString("id"), pcName = pc.optString("name", "PC"),
                ramTotal = hw.long("ram_total"), ramAvailable = hw.long("ram_available"),
                gpus = (0 until gpus.length()).map { gpus.getJSONObject(it).let { g -> g.optString("name", "GPU") } },
                backend = hw.str("backend"), memoryPool = hw.str("memory_pool"), memoryBudget = hw.long("memory_budget"),
                running = rt.optBoolean("running", false), model = rt.str("model"), context = rt.int("context"),
                gpuLayers = rt.int("gpu_layers"),
                generationCheck = rt.optJSONObject("generation_check")?.str("state"),
                tls = tr.optBoolean("tls", false), tlsFingerprint = tr.str("tls_fingerprint_sha256"),
                models = (0 until models.length()).map {
                    val m = models.getJSONObject(it)
                    ModelFit(m.optString("name"), m.optString("fit"), m.int("ctx"), m.optString("verified", "untested"), m.long("size"))
                },
            )
        }
    }
}

/** Everything the screen needs to say, with no silent success. */
sealed class CompanionState {
    data class Online(val report: CompanionReport, val notes: List<String>) : CompanionState()
    data class Offline(val reason: String) : CompanionState()
    /** The PC answered but refused the key (wrong, revoked or rotated). */
    object BadKey : CompanionState()
    /** Certificate not trusted, pin mismatch or host name does not match: never connect anyway. */
    data class WrongHost(val reason: String) : CompanionState()
    /** Same address, different PC identity than the one pinned at pairing. */
    data class ChangedPc(val pinned: String, val now: String, val report: CompanionReport) : CompanionState()
    data class Unsupported(val reason: String) : CompanionState()
}

object ProfileExport {
    /** Profiles for backup/sharing. Never includes keys; the pinned certificate hash is public information. */
    fun toJson(profiles: List<VictorProfile>): String = JSONArray().also { arr ->
        profiles.forEach { p ->
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("origin", p.origin)
                .put("pinnedCertSha256", p.pinnedCertSha256 ?: JSONObject.NULL).put("pcId", p.pcId ?: JSONObject.NULL).put("tunnel", p.tunnel))
        }
    }.toString()

    fun fromJson(text: String): List<VictorProfile> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val ok = OriginPolicy.parse(o.optString("origin"), o.optBoolean("tunnel", false)) as? OriginPolicy.Result.Ok ?: return@mapNotNull null
            VictorProfile(o.optString("id").ifBlank { java.util.UUID.randomUUID().toString() }, o.optString("name", "VictorLinux PC"), ok.origin,
                o.optString("pinnedCertSha256").takeIf { Fingerprint.isValid(it) }, o.optString("pcId").takeIf { it.isNotBlank() && it != "null" }, null,
                o.optBoolean("tunnel", false))
        }
    }
}

object Fingerprint {
    fun normalize(s: String): String = s.trim().lowercase().replace(":", "").replace(" ", "")
    fun isValid(s: String?): Boolean = s != null && Regex("^[0-9a-f]{64}$").matches(normalize(s))
}
