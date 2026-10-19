package com.victorious.browser.urltools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Link shortening via TinyURL's keyless create endpoint. No API key, no account —
 * just a GET that returns the short URL as a plain-text body (or a string starting
 * with "Error" on failure, which [shorten] treats as a failure rather than a result).
 *
 * [buildRequestUrl] is the one pure, unit-testable piece; the actual HTTP call in
 * [shorten] is not covered by a unit test (no network in this project's test
 * environment) and has not been exercised against the real endpoint in this session.
 */
object LinkShortener {
    private const val ENDPOINT = "https://tinyurl.com/api-create.php"
    private const val TIMEOUT_MS = 8_000

    fun buildRequestUrl(longUrl: String): String = "$ENDPOINT?url=${UrlTools.encode(longUrl)}"

    suspend fun shorten(longUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(buildRequestUrl(longUrl)).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.requestMethod = "GET"
            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            when {
                code != 200 -> Result.failure(IllegalStateException("HTTP $code"))
                body.startsWith("Error", ignoreCase = true) -> Result.failure(IllegalStateException(body))
                body.isBlank() -> Result.failure(IllegalStateException("Empty response"))
                else -> Result.success(body)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
