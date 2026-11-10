package com.victorious.browser.osint.research

import org.json.JSONObject

/**
 * DNS-over-HTTPS (JSON API) URL building + response parsing. Pure; the fetch is done
 * by [ResearchNet] with the app's bounded HTTP stack.
 */
object Doh {
    enum class Provider(val label: String, val base: String) {
        CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query"),
        GOOGLE("Google", "https://dns.google/resolve"),
    }

    /** Common RR type numbers → names (RFC 1035 / 3596 / 6844 …). */
    val TYPES: Map<Int, String> = mapOf(
        1 to "A", 2 to "NS", 5 to "CNAME", 6 to "SOA", 12 to "PTR", 15 to "MX", 16 to "TXT",
        28 to "AAAA", 33 to "SRV", 43 to "DS", 48 to "DNSKEY", 65 to "HTTPS", 257 to "CAA",
    )

    val STATUS: Map<Int, String> = mapOf(
        0 to "NOERROR", 1 to "FORMERR", 2 to "SERVFAIL", 3 to "NXDOMAIN", 4 to "NOTIMP", 5 to "REFUSED",
    )

    data class Record(val name: String, val type: String, val ttl: Int, val data: String)
    data class Answer(val status: Int, val records: List<Record>) {
        val statusText: String get() = STATUS[status] ?: "RCODE $status"
        val ok: Boolean get() = status == 0
    }

    private val NAME = Regex("^(?=.{1,253}\\.?$)(?:[A-Za-z0-9_](?:[A-Za-z0-9_-]{0,61}[A-Za-z0-9])?\\.)*[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.?$")

    fun isValidName(name: String): Boolean = NAME.matches(name.trim())

    fun queryUrl(provider: Provider, name: String, type: String): String =
        "${provider.base}?name=${DorkComposer.encode(name.trim().trimEnd('.'))}&type=${DorkComposer.encode(type.uppercase())}"

    /** Header required by Cloudflare's JSON endpoint (harmless for Google). */
    val HEADERS = mapOf("Accept" to "application/dns-json")

    fun parse(json: String): Answer {
        val o = JSONObject(json)
        val status = o.optInt("Status", -1)
        val arr = o.optJSONArray("Answer")
        val records = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                val t = r.optInt("type")
                add(Record(r.optString("name").trimEnd('.'), TYPES[t] ?: "TYPE$t", r.optInt("TTL"), unquoteTxt(r.optString("data"))))
            }
        }
        return Answer(status, records)
    }

    /** DoH returns TXT data as one or more quoted strings: `"v=spf1 " "-all"` → `v=spf1 -all`. */
    fun unquoteTxt(data: String): String {
        val d = data.trim()
        if (!d.startsWith("\"")) return d
        return Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(d).joinToString("") { it.groupValues[1].replace("\\\"", "\"") }
    }

    /** Reverse-DNS name for an IPv4 address (`1.2.3.4` → `4.3.2.1.in-addr.arpa`). */
    fun ptrName(ipv4: String): String? {
        val parts = ipv4.trim().split('.')
        if (parts.size != 4 || parts.any { p -> p.toIntOrNull()?.let { it in 0..255 } != true }) return null
        return parts.reversed().joinToString(".") + ".in-addr.arpa"
    }
}
