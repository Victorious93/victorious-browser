package com.victorious.browser.headscale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Headscale control-server REST API client (`/api/v1/...` endpoints), for the subset of
 * operations an "advanced Headscale settings" screen needs: list nodes, list and
 * create pre-auth keys, expire a node or a pre-auth key.
 *
 * [Guessing, not verified] — unlike `UrlScanner`'s URLhaus/VirusTotal integration
 * (which this session confirmed live against the real public APIs), **Headscale is
 * self-hosted: there is no single public server to test against**, so the request/
 * response shapes here are built from Headscale's published API documentation and
 * this model's training knowledge, not a captured real response. Headscale's API
 * has genuinely changed across versions (e.g. the `user` field on pre-auth keys has
 * moved between a username string and a numeric user ID across releases) — treat
 * the request bodies below as a starting point to verify against your own server's
 * actual version, not a guaranteed-correct client.
 */
object HeadscaleApiClient {
    data class Node(
        val id: String,
        val name: String,
        val userName: String,
        val ipAddresses: List<String>,
        val online: Boolean,
        val lastSeen: String?,
        val expiry: String?,
    )

    data class PreAuthKey(
        val id: String,
        val key: String,
        val reusable: Boolean,
        val ephemeral: Boolean,
        val used: Boolean,
        val expiration: String?,
    )

    private const val TIMEOUT_MS = 8_000

    /** Interprets a `GET /api/v1/node` response body. Pure; unit-tested against hand-written JSON. */
    fun parseNodesJson(body: String): List<Node> {
        val nodes = JSONObject(body).optJSONArray("nodes") ?: JSONArray()
        return buildList {
            for (i in 0 until nodes.length()) {
                val n = nodes.getJSONObject(i)
                val user = n.optJSONObject("user")
                add(
                    Node(
                        id = n.optString("id"),
                        name = n.optString("name"),
                        userName = user?.optString("name").orEmpty(),
                        ipAddresses = n.optJSONArray("ipAddresses")?.let { arr ->
                            buildList { for (j in 0 until arr.length()) add(arr.getString(j)) }
                        }.orEmpty(),
                        online = n.optBoolean("online", false),
                        lastSeen = n.optString("lastSeen").takeIf { it.isNotBlank() },
                        expiry = n.optString("expiry").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    /** Interprets a `GET /api/v1/preauthkey` response body. */
    fun parsePreAuthKeysJson(body: String): List<PreAuthKey> {
        val keys = JSONObject(body).optJSONArray("preAuthKeys") ?: JSONArray()
        return buildList {
            for (i in 0 until keys.length()) {
                val k = keys.getJSONObject(i)
                add(
                    PreAuthKey(
                        id = k.optString("id"),
                        key = k.optString("key"),
                        reusable = k.optBoolean("reusable", false),
                        ephemeral = k.optBoolean("ephemeral", false),
                        used = k.optBoolean("used", false),
                        expiration = k.optString("expiration").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    /** Builds a `POST /api/v1/preauthkey` request body. */
    fun buildCreatePreAuthKeyBody(user: String, reusable: Boolean, ephemeral: Boolean, expirationIso: String?): String {
        val obj = JSONObject()
            .put("user", user)
            .put("reusable", reusable)
            .put("ephemeral", ephemeral)
        expirationIso?.let { obj.put("expiration", it) }
        return obj.toString()
    }

    private fun request(
        serverUrl: String,
        apiKey: String,
        path: String,
        method: String,
        body: String? = null,
    ): Result<String> {
        return try {
            val connection = URL(serverUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val responseBody = (if (code in 200..299) connection.inputStream else connection.errorStream)
                .bufferedReader().use { it.readText() }
            if (code !in 200..299) {
                Result.failure(IllegalStateException("Headscale HTTP $code: $responseBody"))
            } else {
                Result.success(responseBody)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listNodes(serverUrl: String, apiKey: String): Result<List<Node>> = withContext(Dispatchers.IO) {
        request(serverUrl, apiKey, "/api/v1/node", "GET").mapCatching { parseNodesJson(it) }
    }

    suspend fun listPreAuthKeys(serverUrl: String, apiKey: String, user: String): Result<List<PreAuthKey>> = withContext(Dispatchers.IO) {
        request(serverUrl, apiKey, "/api/v1/preauthkey?user=${java.net.URLEncoder.encode(user, "UTF-8")}", "GET")
            .mapCatching { parsePreAuthKeysJson(it) }
    }

    suspend fun createPreAuthKey(
        serverUrl: String,
        apiKey: String,
        user: String,
        reusable: Boolean,
        ephemeral: Boolean,
        expirationIso: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        request(
            serverUrl,
            apiKey,
            "/api/v1/preauthkey",
            "POST",
            buildCreatePreAuthKeyBody(user, reusable, ephemeral, expirationIso),
        )
    }

    suspend fun expireNode(serverUrl: String, apiKey: String, nodeId: String): Result<String> = withContext(Dispatchers.IO) {
        request(serverUrl, apiKey, "/api/v1/node/$nodeId/expire", "POST")
    }
}
