package com.victorious.browser.msf

/**
 * Pure (network-free) request/response mapping for the Metasploit msgrpc API.
 * Unit-tested; [MsfRpcClient] layers HTTP transport on top.
 *
 * Wire format: each call is a MessagePack array `[method, token?, arg...]`.
 * `auth.login` carries no token; every other call puts the auth token first.
 * Responses are MessagePack maps; errors carry `error == true` with
 * `error_message` / `error_class`.
 */
object MsfProtocol {

    const val DEFAULT_PATH = "/api/1.0/"

    /** Build the argument list for a call (before msgpack encoding). */
    fun buildCall(method: String, token: String?, args: List<Any?>): List<Any?> {
        val list = ArrayList<Any?>()
        list.add(method)
        if (method != "auth.login" && !token.isNullOrEmpty()) {
            list.add(token)
        }
        list.addAll(args)
        return list
    }

    /** MessagePack-encode a full call. */
    fun encodeCall(method: String, token: String?, args: List<Any?>): ByteArray =
        MsgPack.encode(buildCall(method, token, args))

    /** Coerce a decoded msgpack value (String or ByteArray) to String. */
    fun asString(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is ByteArray -> String(value, Charsets.UTF_8)
        else -> value.toString()
    }

    /** Look up a key in a decoded map allowing String or byte-array keys. */
    fun mapGet(map: Any?, key: String): Any? {
        if (map !is Map<*, *>) return null
        for ((k, v) in map) {
            if (asString(k) == key) return v
        }
        return null
    }

    fun isError(response: Any?): Boolean {
        val err = mapGet(response, "error")
        return err == true
    }

    fun errorMessage(response: Any?): String? =
        asString(mapGet(response, "error_message"))
            ?: asString(mapGet(response, "error_class"))

    fun extractToken(response: Any?): String? = asString(mapGet(response, "token"))

    fun resultString(response: Any?): String? = asString(mapGet(response, "result"))
}
