package com.victorious.browser.msf

import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Client for the Metasploit RPC API (MessagePack over HTTP — the `msgrpc` /
 * `msgpack-rpc` interface). POSTs a msgpack-encoded `[method, token?, args...]`
 * array to `/api/1.0/` and decodes the msgpack map response.
 *
 * Nothing connects until the caller supplies host/port/creds and calls [login].
 * The msgrpc service ships with a self-signed TLS cert by default, so when [ssl]
 * is on this client uses a permissive trust manager scoped to this client only
 * (this is the owner's own MSF host on a trusted/mesh network). The real
 * request/response mapping is in [MsfProtocol] and is unit-tested.
 */
class MsfRpcClient(
    private val host: String,
    private val port: Int,
    private val ssl: Boolean,
) {
    @Volatile var token: String? = null
        private set

    val isAuthenticated: Boolean get() = !token.isNullOrEmpty()

    private val endpoint: String
        get() = (if (ssl) "https" else "http") + "://" + host + ":" + port + MsfProtocol.DEFAULT_PATH

    data class CallResult(
        val ok: Boolean,
        val value: Any?,
        val error: String? = null,
    )

    fun login(username: String, password: String): CallResult {
        val res = rawCall("auth.login", listOf(username, password))
        if (!res.ok) return res
        val tok = MsfProtocol.extractToken(res.value)
        return if (tok != null) {
            token = tok
            CallResult(ok = true, value = res.value)
        } else {
            CallResult(ok = false, value = res.value, error = MsfProtocol.errorMessage(res.value) ?: "No token returned")
        }
    }

    fun logout(): CallResult {
        val tok = token ?: return CallResult(ok = true, value = null)
        val res = rawCall("auth.logout", listOf(tok))
        token = null
        return res
    }

    fun consoleCreate(): CallResult = call("console.create", emptyList())
    fun consoleList(): CallResult = call("console.list", emptyList())
    fun consoleRead(consoleId: String): CallResult = call("console.read", listOf(consoleId))
    fun consoleWrite(consoleId: String, data: String): CallResult =
        call("console.write", listOf(consoleId, if (data.endsWith("\n")) data else data + "\n"))
    fun consoleDestroy(consoleId: String): CallResult = call("console.destroy", listOf(consoleId))
    fun sessionList(): CallResult = call("session.list", emptyList())

    /** Generic authenticated call. */
    fun call(method: String, args: List<Any?>): CallResult = rawCall(method, args)

    private fun rawCall(method: String, args: List<Any?>): CallResult {
        return try {
            val body = MsfProtocol.encodeCall(method, token, args)
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            if (conn is HttpsURLConnection && ssl) {
                conn.sslSocketFactory = permissiveSocketFactory()
                conn.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "binary/message-pack")
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val respBytes = stream?.readBytes()
            conn.disconnect()
            if (respBytes == null || respBytes.isEmpty()) {
                return CallResult(ok = false, value = null, error = "Empty response (HTTP $code)")
            }
            val decoded = MsgPack.decode(respBytes)
            if (MsfProtocol.isError(decoded)) {
                CallResult(ok = false, value = decoded, error = MsfProtocol.errorMessage(decoded) ?: "RPC error")
            } else {
                CallResult(ok = true, value = decoded)
            }
        } catch (t: Throwable) {
            CallResult(ok = false, value = null, error = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun permissiveSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, trustAll, SecureRandom())
        return ctx.socketFactory
    }
}
