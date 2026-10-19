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
 * Two backends, both optional in the sense that a failure of one doesn't block the
 * other:
 * - **URLhaus** (abuse.ch) — free, keyless, no signup. A known-malware-URL database;
 *   a hit means the URL is specifically known as a malware distribution point, a
 *   miss means only "not in this one database", not "safe".
 * - **VirusTotal** — requires the operator's own API key (see [VirusTotalSettings]);
 *   not called when no key is set. Multi-engine URL/file reputation; submission +
 *   polling, since VirusTotal analyses are asynchronous.
 *
 * The network calls ([checkUrlHaus], [checkVirusTotal]) are not unit-tested (no
 * network in this project's test environment, and VirusTotal in particular has not
 * been exercised against the real API in this session at all — no key was available
 * to test with). [interpretUrlHausJson] and [interpretVirusTotalAnalysisJson] are the
 * pure, unit-tested response-parsing halves.
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

    /** Interprets a URLhaus `/v1/url/` response body. Throws on malformed JSON (caught by [checkUrlHaus]). */
    fun interpretUrlHausJson(body: String): ScanVerdict {
        val json = JSONObject(body)
        return when (val status = json.optString("query_status")) {
            "ok" -> {
                val threat = json.optString("threat", "unknown")
                val urlStatus = json.optString("url_status", "unknown")
                ScanVerdict("URLhaus", malicious = true, detail = "Listed as $threat ($urlStatus)")
            }
            "no_results" -> ScanVerdict("URLhaus", malicious = false, detail = "Not listed in URLhaus")
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

    suspend fun checkUrlHaus(url: String): Result<ScanVerdict> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(URLHAUS_ENDPOINT).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.use { it.write("url=${UrlTools.encode(url)}".toByteArray()) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                Result.failure(IllegalStateException("URLhaus HTTP $code"))
            } else {
                Result.success(interpretUrlHausJson(body))
            }
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
