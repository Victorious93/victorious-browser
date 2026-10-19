package com.victorious.browser.urltools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * URL reputation / malware checks before the operator opens a link.
 *
 * Two backends, both requiring the operator's own API key — **neither is keyless**.
 * [checkUrlHaus] needs a URLhaus Auth-Key (free account at https://auth.abuse.ch/,
 * see [UrlHausSettings]); [checkVirusTotal] needs a VirusTotal API key (see
 * [VirusTotalSettings]). Neither is called when its key is blank. Both failing
 * independently doesn't block the other.
 *
 * [Certain, verified live in this session via `curl` against both real endpoints —
 * not assumed]: as of this writing, URLhaus requires an `Auth-Key` header on every
 * request (no key → HTTP 401 with a plain `{"error":"Unauthorized"}` body; a
 * present-but-wrong key → HTTP 403 with `{"query_status":"unknown_auth_key"}`).
 * An earlier version of this file and its docs claimed URLhaus was "free and
 * keyless" — that was true once but is not true of the live API today, and this
 * comment exists specifically so that claim doesn't silently drift back into being
 * wrong. VirusTotal's unauthenticated response was also confirmed live: HTTP 401,
 * `{"error":{"code":"AuthenticationRequiredError", ...}}`.
 *
 * [checkUrlHaus]'s and [checkVirusTotal]'s HTTP plumbing is still not unit-tested
 * (no network in the Gradle test sandbox) and neither has been exercised live with
 * a *valid* key — only the "no key" / "garbage key" error shapes have been verified
 * against the real services. [interpretUrlHausJson] and
 * [interpretVirusTotalAnalysisJson] are the pure, unit-tested response-parsing
 * halves, and their "no results" / "unknown auth key" branches are built from the
 * real bodies captured above, not guessed.
 */
object UrlScanner {
    data class ScanVerdict(val source: String, val malicious: Boolean?, val detail: String)

    sealed class VtAnalysisResult {
        data class Completed(val malicious: Int, val suspicious: Int, val harmless: Int) : VtAnalysisResult()
        data object Pending : VtAnalysisResult()
    }

    private const val URLHAUS_ENDPOINT = "https://urlhaus-api.abuse.ch/v1/url/"
    private const val VT_SUBMIT_ENDPOINT = "https://www.virustotal.com/api/v3/urls"
    private const val VT_ANALYSIS_ENDPOINT = "https://www.virustotal.com/api/v3/analyses/"
    private const val TIMEOUT_MS = 8_000

    /**
     * Interprets a URLhaus `/v1/url/` response body — including the two
     * authentication-failure shapes abuse.ch actually returns (verified live, see
     * this object's doc): a plain `{"error": "..."}` body (no `Auth-Key` header sent
     * at all) and `{"query_status": "unknown_auth_key"}` (a key was sent but
     * rejected). Throws on malformed JSON (caught by [checkUrlHaus]).
     */
    fun interpretUrlHausJson(body: String): ScanVerdict {
        val json = JSONObject(body)
        if (json.has("error")) {
            return ScanVerdict("URLhaus", malicious = null, detail = "Auth-Key missing or rejected: ${json.optString("error")}")
        }
        return when (val status = json.optString("query_status")) {
            "ok" -> {
                val threat = json.optString("threat", "unknown")
                val urlStatus = json.optString("url_status", "unknown")
                ScanVerdict("URLhaus", malicious = true, detail = "Listed as $threat ($urlStatus)")
            }
            "no_results" -> ScanVerdict("URLhaus", malicious = false, detail = "Not listed in URLhaus")
            "unknown_auth_key" -> ScanVerdict("URLhaus", malicious = null, detail = "Auth-Key rejected — check the key in settings")
            "missing_auth_key" -> ScanVerdict("URLhaus", malicious = null, detail = "Auth-Key required — add one in settings")
            else -> ScanVerdict("URLhaus", malicious = null, detail = "Lookup inconclusive: $status")
        }
    }

    /** Interprets one VirusTotal `/v3/analyses/{id}` response body. */
    fun interpretVirusTotalAnalysisJson(body: String): VtAnalysisResult {
        val data = JSONObject(body).getJSONObject("data")
        val attributes = data.getJSONObject("attributes")
        if (attributes.optString("status") != "completed") return VtAnalysisResult.Pending
        val stats = attributes.getJSONObject("stats")
        return VtAnalysisResult.Completed(
            malicious = stats.optInt("malicious", 0),
            suspicious = stats.optInt("suspicious", 0),
            harmless = stats.optInt("harmless", 0),
        )
    }

    /**
     * Requires [authKey] (a free account at https://auth.abuse.ch/) — not called when
     * blank, same as [checkVirusTotal]'s key requirement. A non-2xx response is still
     * handed to [interpretUrlHausJson] rather than treated as an opaque failure: both
     * of abuse.ch's auth-failure shapes are real JSON bodies worth surfacing (see this
     * object's doc), not just an HTTP status code.
     */
    suspend fun checkUrlHaus(url: String, authKey: String): Result<ScanVerdict> = withContext(Dispatchers.IO) {
        if (authKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("No URLhaus Auth-Key set"))
        try {
            val connection = URL(URLHAUS_ENDPOINT).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Auth-Key", authKey)
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write("url=${UrlTools.encode(url)}".toByteArray()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            Result.success(interpretUrlHausJson(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Submits [url] to VirusTotal and polls its analysis up to 3 times (short delays
     * between polls) before giving up and reporting the scan as still queued rather
     * than blocking indefinitely. Requires [apiKey]; the key is sent only to
     * VirusTotal's own API over HTTPS, never logged, never sent anywhere else.
     */
    suspend fun checkVirusTotal(url: String, apiKey: String): Result<ScanVerdict> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("No VirusTotal API key set"))
        try {
            val submit = URL(VT_SUBMIT_ENDPOINT).openConnection() as HttpURLConnection
            submit.requestMethod = "POST"
            submit.doOutput = true
            submit.connectTimeout = TIMEOUT_MS
            submit.readTimeout = TIMEOUT_MS
            submit.setRequestProperty("x-apikey", apiKey)
            submit.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            submit.outputStream.use { it.write("url=${UrlTools.encode(url)}".toByteArray()) }
            val submitCode = submit.responseCode
            val submitBody = (if (submitCode in 200..299) submit.inputStream else submit.errorStream)
                .bufferedReader().use { it.readText() }
            if (submitCode !in 200..299) {
                return@withContext Result.failure(IllegalStateException("VirusTotal submit HTTP $submitCode"))
            }
            val analysisId = JSONObject(submitBody).getJSONObject("data").getString("id")

            repeat(3) { attempt ->
                delay(if (attempt == 0) 2_000L else 3_000L)
                val poll = URL(VT_ANALYSIS_ENDPOINT + analysisId).openConnection() as HttpURLConnection
                poll.connectTimeout = TIMEOUT_MS
                poll.readTimeout = TIMEOUT_MS
                poll.setRequestProperty("x-apikey", apiKey)
                val pollCode = poll.responseCode
                val pollBody = (if (pollCode in 200..299) poll.inputStream else poll.errorStream)
                    .bufferedReader().use { it.readText() }
                if (pollCode !in 200..299) {
                    return@withContext Result.failure(IllegalStateException("VirusTotal poll HTTP $pollCode"))
                }
                when (val result = interpretVirusTotalAnalysisJson(pollBody)) {
                    is VtAnalysisResult.Completed -> return@withContext Result.success(
                        ScanVerdict(
                            "VirusTotal",
                            malicious = (result.malicious + result.suspicious) > 0,
                            detail = "${result.malicious} malicious, ${result.suspicious} suspicious, ${result.harmless} harmless",
                        ),
                    )
                    VtAnalysisResult.Pending -> Unit // keep polling
                }
            }
            Result.success(ScanVerdict("VirusTotal", malicious = null, detail = "Scan still queued — try again shortly"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
