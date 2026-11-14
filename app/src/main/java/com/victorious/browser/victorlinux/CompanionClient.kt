package com.victorious.browser.victorlinux

import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

/**
 * Talks to ONE saved PC. The API key is attached only to requests for that profile's exact origin
 * ([OriginPolicy.sameOrigin]); redirects are never followed, so a key cannot be bounced to another host.
 * Reads [/victor/companion] and calls [/v1/chat/completions]. Nothing here uploads anything else.
 */
class CompanionClient(
    private val profile: VictorProfile,
    private val key: () -> String?,
    private val connectTimeoutMs: Int = 4_000,
    private val readTimeoutMs: Int = 20_000,
) {
    private fun open(path: String, method: String): HttpURLConnection {
        val url = profile.origin + path
        require(OriginPolicy.sameOrigin(profile.origin, url)) { "refusing to send the key to a different origin" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.connectTimeout = connectTimeoutMs; c.readTimeout = readTimeoutMs; c.requestMethod = method
        if (c is HttpsURLConnection) {
            PinnedTls.socketFactory(profile.pinnedCertSha256)?.let { c.sslSocketFactory = it }
            c.hostnameVerifier = PinnedTls.hostnameVerifier()
        }
        key()?.let { c.setRequestProperty("Authorization", "Bearer $it") }
        c.setRequestProperty("Accept", "application/json")
        return c
    }

    /** One status refresh, classified. Never throws for expected network states. */
    fun fetchStatus(): CompanionState {
        val c = try { open("/victor/companion", "GET") } catch (e: IllegalArgumentException) { return CompanionState.WrongHost(e.message ?: "bad origin") }
        try {
            when (val code = c.responseCode) {
                200 -> {}
                401, 403 -> return CompanionState.BadKey
                in 300..399 -> return CompanionState.WrongHost("The PC redirected the request to another address; the key was not sent there.")
                404 -> return CompanionState.Unsupported("This address answers, but it is not a VictorLinux gateway with the companion report (HTTP 404).")
                503 -> return CompanionState.Offline("The gateway is up but the model is not running (HTTP 503).")
                else -> return CompanionState.Unsupported("Unexpected answer from the PC (HTTP $code).")
            }
            val body = c.inputStream.use { it.readBytes().take(1_000_000).toByteArray().toString(Charsets.UTF_8) }
            val report = try { CompanionReport.parse(body) } catch (e: Exception) { return CompanionState.Unsupported("The PC sent a report this app cannot read: ${e.message}") }
            return evaluate(report)
        } catch (e: SSLException) {
            return CompanionState.WrongHost(sslReason(e))
        } catch (e: UnknownHostException) {
            return CompanionState.Offline("The name ${profile.origin} cannot be found. Is the VPN/mesh connected, or has the PC's address changed?")
        } catch (e: ConnectException) {
            return CompanionState.Offline("The PC refused the connection. The gateway may be stopped, or the port changed. If you use a tunnel, check that it is running.")
        } catch (e: SocketTimeoutException) {
            return CompanionState.Offline("The PC did not answer in time. It may be off, asleep, or on another network.")
        } catch (e: IOException) {
            return CompanionState.Offline("Network error: ${e.message}")
        } finally { c.disconnect() }
    }

    fun evaluate(report: CompanionReport): CompanionState {
        val pinned = profile.pcId
        if (pinned != null && pinned != report.pcId) return CompanionState.ChangedPc(pinned, report.pcId, report)
        val notes = mutableListOf<String>()
        if (profile.lastModel != null && report.model != null && profile.lastModel != report.model) notes += "The loaded model changed from ${profile.lastModel} to ${report.model}."
        if (!report.running) notes += "No model is running on the PC."
        return CompanionState.Online(report, notes)
    }

    private fun sslReason(e: SSLException): String {
        val m = (e.cause?.message ?: e.message).orEmpty()
        return when {
            "pinned" in m -> "The PC's certificate does not match the fingerprint you pinned. Do not continue unless you changed the PC's certificate; compare the fingerprint in the PC's Control Center."
            "No subject alternative" in m || "hostname" in m.lowercase() -> "The certificate is not valid for this address. Re-create it on the PC with victor-tls-cert <this address>."
            else -> "The PC's certificate is not trusted ($m). For a self-signed PC, pin its SHA-256 fingerprint from the Control Center. Certificate errors are never ignored."
        }
    }

    sealed class Chat {
        data class Reply(val text: String) : Chat()
        data class Failed(val state: CompanionState?, val message: String) : Chat()
    }

    /** One non-streaming chat turn. Messages are sent only to this PC; nothing is stored here. */
    fun chat(messages: List<Pair<String, String>>, maxTokens: Int = 512): Chat {
        val c = try { open("/v1/chat/completions", "POST") } catch (e: IllegalArgumentException) { return Chat.Failed(null, e.message ?: "bad origin") }
        return try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            val arr = org.json.JSONArray().also { a -> messages.forEach { (r, t) -> a.put(org.json.JSONObject().put("role", r).put("content", t)) } }
            val body = org.json.JSONObject().put("model", "victor-local").put("messages", arr).put("max_tokens", maxTokens).put("stream", false).toString()
            c.outputStream.use { it.write(body.toByteArray()) }
            when (val code = c.responseCode) {
                200 -> {
                    val text = org.json.JSONObject(c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) })
                        .getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "")
                    Chat.Reply(text)
                }
                401, 403 -> Chat.Failed(CompanionState.BadKey, "The PC rejected the key.")
                503 -> Chat.Failed(CompanionState.Offline("model not running"), "The model is not running on the PC.")
                else -> Chat.Failed(null, "The PC answered HTTP $code.")
            }
        } catch (e: SSLException) { Chat.Failed(CompanionState.WrongHost(sslReason(e)), "Certificate problem.")
        } catch (e: IOException) { Chat.Failed(CompanionState.Offline(e.message ?: "network error"), "Could not reach the PC.")
        } finally { c.disconnect() }
    }
}
