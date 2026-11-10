package com.victorious.browser.osint.research

import org.json.JSONArray
import org.json.JSONObject

/** RDAP (RFC 9083) — the structured, public successor to WHOIS. Pure URL + parse. */
object Rdap {
    /** rdap.org is a public bootstrap redirector to the authoritative registry/RIR server. */
    fun domainUrl(domain: String) = "https://rdap.org/domain/" + DorkComposer.encode(domain.trim().lowercase().trimEnd('.'))
    fun ipUrl(ip: String) = "https://rdap.org/ip/" + ip.trim()

    data class Info(
        val kind: String,
        val handle: String?,
        val name: String?,
        val status: List<String>,
        val events: List<Pair<String, String>>,
        val nameservers: List<String>,
        val entities: List<Pair<String, String>>,
        val range: String?,
        val country: String?,
    ) {
        fun event(action: String): String? = events.firstOrNull { it.first.equals(action, true) }?.second
    }

    fun parse(json: String): Info {
        val o = JSONObject(json)
        val kind = o.optString("objectClassName", "unknown")
        val status = o.optJSONArray("status").strings()
        val events = buildList {
            val arr = o.optJSONArray("events")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                add(e.optString("eventAction") to e.optString("eventDate"))
            }
        }
        val ns = buildList {
            val arr = o.optJSONArray("nameservers")
            if (arr != null) for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.optString("ldhName")?.takeIf { it.isNotBlank() }?.let { add(it.lowercase()) }
            }
        }
        val entities = buildList { collectEntities(o.optJSONArray("entities"), this, 0) }
        val range = if (o.has("startAddress")) "${o.optString("startAddress")} – ${o.optString("endAddress")}" else null
        val name = o.optString("ldhName").ifBlank { o.optString("name") }.ifBlank { null }
        return Info(
            kind = kind,
            handle = o.optString("handle").ifBlank { null },
            name = name,
            status = status,
            events = events,
            nameservers = ns,
            entities = entities,
            range = range,
            country = o.optString("country").ifBlank { null },
        )
    }

    private fun collectEntities(arr: JSONArray?, out: MutableList<Pair<String, String>>, depth: Int) {
        if (arr == null || depth > 2) return
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val roles = e.optJSONArray("roles").strings().joinToString(",").ifBlank { "entity" }
            val fn = vcardFn(e.optJSONArray("vcardArray")) ?: e.optString("handle").ifBlank { null }
            if (fn != null) out += roles to fn
            collectEntities(e.optJSONArray("entities"), out, depth + 1)
        }
    }

    /** jCard: ["vcard", [["version",{},"text","4.0"], ["fn",{},"text","Name"], …]] */
    fun vcardFn(vcard: JSONArray?): String? {
        val props = vcard?.optJSONArray(1) ?: return null
        for (i in 0 until props.length()) {
            val p = props.optJSONArray(i) ?: continue
            if (p.optString(0) == "fn") return p.optString(3).ifBlank { null }
        }
        return null
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifBlank { null } }
}

/** Certificate Transparency subdomain enumeration via crt.sh's public JSON output. */
object CrtSh {
    fun url(domain: String) = "https://crt.sh/?q=" + DorkComposer.encode("%." + domain.trim().lowercase()) + "&output=json"
    fun webUrl(domain: String) = "https://crt.sh/?q=" + DorkComposer.encode("%." + domain.trim().lowercase())

    /** Distinct names under [domain] (wildcards stripped), sorted. Tolerates a truncated body. */
    fun parseSubdomains(json: String, domain: String): List<String> {
        val d = domain.trim().lowercase().trimEnd('.')
        val names = sortedSetOf<String>()
        val arr = runCatching { JSONArray(json) }.getOrElse { return salvage(json, d) }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            listOf(o.optString("name_value"), o.optString("common_name")).forEach { v ->
                v.split('\n').forEach { add(it, d, names) }
            }
        }
        return names.toList()
    }

    private fun add(raw: String, d: String, out: MutableSet<String>) {
        val n = raw.trim().lowercase().removePrefix("*.").trimEnd('.')
        if (n.isNotEmpty() && (n == d || n.endsWith(".$d")) && !n.contains(' ') && !n.contains('@')) out += n
    }

    /** When the size cap cut the JSON, pull names out with a regex instead. */
    private fun salvage(text: String, d: String): List<String> {
        val out = sortedSetOf<String>()
        Regex("\"name_value\":\"([^\"]*)\"").findAll(text).forEach { m ->
            m.groupValues[1].split("\\n").forEach { add(it, d, out) }
        }
        return out.toList()
    }
}

/** Pivots for a domain or IP that open public pages in a tab. */
object InfraPivots {
    private val IPV4 = Regex("^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
    fun isIpv4(s: String) = IPV4.matches(s.trim())
    fun isIpv6(s: String): Boolean {
        val t = s.trim()
        return t.count { it == ':' } >= 2 && t.length <= 45 &&
            t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.' }
    }

    fun forDomain(d: String): List<Pivot> {
        val e = DorkComposer.encode(d)
        return listOf(
            Pivot("crt.sh certificates", CrtSh.webUrl(d)),
            Pivot("Wayback Machine", "https://web.archive.org/web/*/$e*"),
            Pivot("SecurityTrails (public page)", "https://securitytrails.com/domain/$e/dns"),
            Pivot("DNSDumpster", "https://dnsdumpster.com/"),
            Pivot("urlscan.io search", "https://urlscan.io/search/#domain:$e"),
            Pivot("VirusTotal", "https://www.virustotal.com/gui/domain/$e"),
            Pivot("Shodan (hostname)", "https://www.shodan.io/search?query=hostname%3A$e"),
            Pivot("BuiltWith", "https://builtwith.com/$e"),
            Pivot("ICANN lookup", "https://lookup.icann.org/en/lookup?name=$e"),
        )
    }

    fun forIp(ip: String): List<Pivot> {
        val e = DorkComposer.encode(ip)
        return listOf(
            Pivot("Reverse IP (sites on this IP) — Bing", "https://www.bing.com/search?q=ip%3A$e"),
            Pivot("Reverse IP — HackerTarget page", "https://hackertarget.com/reverse-ip-lookup/"),
            Pivot("Shodan host", "https://www.shodan.io/host/$e"),
            Pivot("Censys host", "https://search.censys.io/hosts/$e"),
            Pivot("ipinfo.io (geo, ASN)", "https://ipinfo.io/$e"),
            Pivot("AbuseIPDB", "https://www.abuseipdb.com/check/$e"),
            Pivot("VirusTotal", "https://www.virustotal.com/gui/ip-address/$e"),
            Pivot("bgp.he.net", "https://bgp.he.net/ip/$e"),
        )
    }
}

/** Have I Been Pwned v3 — only with the user's own API key. */
object Hibp {
    fun breachUrl(email: String) =
        "https://haveibeenpwned.com/api/v3/breachedaccount/" + DorkComposer.encode(email.trim()) + "?truncateResponse=false"

    fun headers(apiKey: String) = mapOf("hibp-api-key" to apiKey.trim(), "User-Agent" to "VictoriousBrowser-DeepResearch")

    data class Breach(val name: String, val domain: String?, val date: String?, val dataClasses: List<String>)

    sealed interface Outcome {
        data class Breached(val breaches: List<Breach>) : Outcome
        data object NotFound : Outcome
        data class Failed(val message: String) : Outcome
    }

    fun interpret(status: Int?, body: String): Outcome = when (status) {
        200 -> runCatching {
            val arr = JSONArray(body)
            Outcome.Breached((0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val dc = o.optJSONArray("DataClasses")
                Breach(
                    o.optString("Name"),
                    o.optString("Domain").ifBlank { null },
                    o.optString("BreachDate").ifBlank { null },
                    if (dc == null) emptyList() else (0 until dc.length()).map { dc.optString(it) },
                )
            })
        }.getOrElse { Outcome.Failed("Unreadable response") }
        404 -> Outcome.NotFound
        401 -> Outcome.Failed("API key rejected (401)")
        403 -> Outcome.Failed("Forbidden (403) — missing User-Agent or blocked")
        429 -> Outcome.Failed("Rate limited (429) — wait and retry")
        null -> Outcome.Failed("No response")
        else -> Outcome.Failed("HTTP $status")
    }
}
