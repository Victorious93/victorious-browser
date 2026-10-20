package com.victorious.browser.osint

import java.net.URLEncoder

/**
 * Pure, Android-free builder for public OSINT pivot links.
 *
 * Given a target (domain / host / IP / username / email / URL) this classifies it
 * and produces the public source URLs that apply. Nothing here fetches anything —
 * the operator opens a chosen link in the WebView.
 */
object OsintSources {

    enum class TargetType { DOMAIN, IP, EMAIL, USERNAME, URL, UNKNOWN }

    enum class Category { CERTS, DNS, ARCHIVE, SCAN, DORK, WHOIS, REPUTATION, PEOPLE }

    /**
     * A single OSINT source. [build] returns the resolved URL for a target, or
     * null when the source does not apply to that particular (already-classified)
     * target value.
     */
    data class Source(
        val id: String,
        val name: String,
        val category: Category,
        val note: String,
        val appliesTo: Set<TargetType>,
        val build: (target: String, type: TargetType) -> String?,
    )

    /** A source paired with the concrete URL it resolved to for a target. */
    data class ResolvedSource(val source: Source, val url: String)

    private val IPV4 = Regex("^(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)$")
    private val DOMAIN = Regex("^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$")
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val USERNAME = Regex("^[A-Za-z0-9._-]{2,40}$")

    /** Heuristically classify [target]. */
    fun classify(target: String): TargetType {
        val t = target.trim()
        if (t.isEmpty()) return TargetType.UNKNOWN
        if (t.startsWith("http://", true) || t.startsWith("https://", true)) return TargetType.URL
        if (EMAIL.matches(t)) return TargetType.EMAIL
        if (IPV4.matches(t)) return TargetType.IP
        if (DOMAIN.matches(t)) return TargetType.DOMAIN
        if (USERNAME.matches(t)) return TargetType.USERNAME
        return TargetType.UNKNOWN
    }

    /** Extract the host from a URL or return the target itself for other types. */
    fun hostOf(target: String, type: TargetType): String {
        if (type != TargetType.URL) return target.trim()
        val stripped = target.trim()
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
        return stripped.substringAfter('@').substringBefore(':')
    }

    /** All built-in sources. */
    val sources: List<Source> = listOf(
        Source(
            "crtsh", "crt.sh", Category.CERTS,
            "Certificate transparency logs — subdomains via issued certs.",
            setOf(TargetType.DOMAIN, TargetType.URL),
        ) { t, ty -> "https://crt.sh/?q=" + enc("%25." + hostOf(t, ty)) },
        Source(
            "dns_google", "DNS (dns.google)", Category.DNS,
            "DNS-over-HTTPS resolver (A records, JSON response).",
            setOf(TargetType.DOMAIN, TargetType.URL),
        ) { t, ty -> "https://dns.google/resolve?name=" + enc(hostOf(t, ty)) + "&type=A" },
        Source(
            "dns_records", "DNS records (nslookup.io)", Category.DNS,
            "Human-readable DNS record browser.",
            setOf(TargetType.DOMAIN, TargetType.URL),
        ) { t, ty -> "https://www.nslookup.io/domains/" + enc(hostOf(t, ty)) + "/dns-records/" },
        Source(
            "reverse_ip", "Reverse IP (viewdns)", Category.DNS,
            "Other hostnames sharing the same IP.",
            setOf(TargetType.IP, TargetType.DOMAIN, TargetType.URL),
        ) { t, ty -> "https://viewdns.info/reverseip/?host=" + enc(hostOf(t, ty)) + "&t=1" },
        Source(
            "wayback", "Wayback Machine", Category.ARCHIVE,
            "Historical snapshots / URL inventory from web.archive.org.",
            setOf(TargetType.DOMAIN, TargetType.URL, TargetType.IP),
        ) { t, ty -> "https://web.archive.org/web/*/" + hostOf(t, ty) + "/*" },
        Source(
            "shodan", "Shodan", Category.SCAN,
            "Host/service exposure (host page for IP, hostname search for domain).",
            setOf(TargetType.IP, TargetType.DOMAIN, TargetType.URL),
        ) { t, ty ->
            if (ty == TargetType.IP) "https://www.shodan.io/host/" + enc(t.trim())
            else "https://www.shodan.io/search?query=" + enc("hostname:" + hostOf(t, ty))
        },
        Source(
            "censys", "Censys", Category.SCAN,
            "Internet-wide host scans (host page for IP, search for domain).",
            setOf(TargetType.IP, TargetType.DOMAIN, TargetType.URL),
        ) { t, ty ->
            if (ty == TargetType.IP) "https://search.censys.io/hosts/" + enc(t.trim())
            else "https://search.censys.io/search?resource=hosts&q=" + enc(hostOf(t, ty))
        },
        Source(
            "google_dork", "Google dork (site:)", Category.DORK,
            "Google results scoped to the target host.",
            setOf(TargetType.DOMAIN, TargetType.URL),
        ) { t, ty ->
            DorkBuilder.searchUrl(
                DorkBuilder.Engine.GOOGLE,
                DorkBuilder.buildQuery(listOf(DorkBuilder.Operator.SITE.format(hostOf(t, ty)))),
            )
        },
        Source(
            "github_dork", "GitHub code search", Category.DORK,
            "Code mentioning the target (secrets, configs, subdomains).",
            setOf(TargetType.DOMAIN, TargetType.URL, TargetType.EMAIL, TargetType.USERNAME),
        ) { t, ty ->
            val q = if (ty == TargetType.URL) hostOf(t, ty) else t.trim()
            "https://github.com/search?q=" + enc(q) + "&type=code"
        },
        Source(
            "whois", "WHOIS (who.is)", Category.WHOIS,
            "Registration / ownership records.",
            setOf(TargetType.DOMAIN, TargetType.IP, TargetType.URL),
        ) { t, ty -> "https://who.is/whois/" + enc(hostOf(t, ty)) },
        Source(
            "virustotal", "VirusTotal", Category.REPUTATION,
            "Reputation and passive intel for domain/IP.",
            setOf(TargetType.DOMAIN, TargetType.IP, TargetType.URL),
        ) { t, ty ->
            val base = "https://www.virustotal.com/gui/"
            when (ty) {
                TargetType.IP -> base + "ip-address/" + enc(t.trim())
                else -> base + "domain/" + enc(hostOf(t, ty))
            }
        },
        Source(
            "whatsmyname", "WhatsMyName", Category.PEOPLE,
            "Username presence across many sites (aggregator).",
            setOf(TargetType.USERNAME),
        ) { t, _ -> "https://whatsmyname.app/?q=" + enc(t.trim()) },
        Source(
            "epieos", "Epieos (email)", Category.PEOPLE,
            "Email footprint / linked accounts lookup.",
            setOf(TargetType.EMAIL),
        ) { t, _ -> "https://epieos.com/?q=" + enc(t.trim()) },
        Source(
            "hibp", "Have I Been Pwned", Category.PEOPLE,
            "Breach exposure search for an email/username.",
            setOf(TargetType.EMAIL, TargetType.USERNAME),
        ) { t, _ -> "https://haveibeenpwned.com/unifiedsearch/" + enc(t.trim()) },
        Source(
            "email_google_dork", "Email dork (Google)", Category.DORK,
            "Google results mentioning the email address.",
            setOf(TargetType.EMAIL),
        ) { t, _ ->
            DorkBuilder.searchUrl(DorkBuilder.Engine.GOOGLE, DorkBuilder.exact(t.trim()))
        },
    )

    /** Resolve every applicable source for [target]. */
    fun sourcesFor(target: String): List<ResolvedSource> {
        val type = classify(target)
        if (type == TargetType.UNKNOWN) return emptyList()
        return sources.mapNotNull { src ->
            if (type !in src.appliesTo) return@mapNotNull null
            val url = src.build(target, type) ?: return@mapNotNull null
            ResolvedSource(src, url)
        }
    }

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
