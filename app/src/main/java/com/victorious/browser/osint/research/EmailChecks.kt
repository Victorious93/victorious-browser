package com.victorious.browser.osint.research

import java.security.MessageDigest

/** Pure email-analysis helpers: syntax, SPF/DMARC/MX interpretation, Gravatar hash, pivots. */
object EmailChecks {

    // Pragmatic (not full RFC 5322) check: local part of common atoms, a dotted domain.
    private val EMAIL = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+)$")

    data class Syntax(val valid: Boolean, val local: String, val domain: String, val problems: List<String>)

    fun syntax(raw: String): Syntax {
        val e = raw.trim()
        val problems = mutableListOf<String>()
        val at = e.lastIndexOf('@')
        val local = if (at > 0) e.substring(0, at) else ""
        val domain = if (at > 0) e.substring(at + 1).lowercase() else ""
        if (at <= 0) problems += "Missing local part or @"
        if (e.length > 254) problems += "Longer than 254 characters"
        if (local.startsWith('.') || local.endsWith('.') || local.contains("..")) problems += "Dots in the wrong place"
        if (domain.isNotEmpty() && !domain.contains('.')) problems += "Domain has no dot"
        val ok = problems.isEmpty() && EMAIL.matches(e)
        if (!ok && problems.isEmpty()) problems += "Invalid characters or domain"
        return Syntax(ok, local, domain, problems)
    }

    fun normalize(email: String): String = email.trim().lowercase()

    /** Gravatar accepts SHA-256 (current) and MD5 (legacy) of the trimmed, lower-cased address. */
    fun gravatarSha256(email: String): String = hex(MessageDigest.getInstance("SHA-256").digest(normalize(email).toByteArray()))
    fun gravatarMd5(email: String): String = hex(MessageDigest.getInstance("MD5").digest(normalize(email).toByteArray()))

    /** `d=404` makes Gravatar answer 404 when no avatar exists (documented behavior). */
    fun gravatarCheckUrl(email: String): String = "https://gravatar.com/avatar/${gravatarSha256(email)}?d=404&s=80"
    fun gravatarProfileUrl(email: String): String = "https://gravatar.com/${gravatarSha256(email)}"

    data class Spf(val record: String?, val all: String?, val includes: List<String>, val multiple: Boolean) {
        val summary: String get() = when {
            record == null -> "No SPF record"
            multiple -> "Multiple SPF records (invalid per RFC 7208)"
            all == "-all" -> "SPF hard-fail (-all)"
            all == "~all" -> "SPF soft-fail (~all)"
            all == "?all" -> "SPF neutral (?all)"
            all == "+all" -> "SPF allows anyone (+all) — weak"
            else -> "SPF present, no 'all' mechanism"
        }
    }

    fun parseSpf(txtRecords: List<String>): Spf {
        val spfs = txtRecords.filter { it.trim().lowercase().startsWith("v=spf1") }
        val rec = spfs.firstOrNull() ?: return Spf(null, null, emptyList(), false)
        val terms = rec.trim().split(Regex("\\s+"))
        val all = terms.lastOrNull { it.lowercase().matches(Regex("[-~?+]?all")) }?.lowercase()?.let { if (it == "all") "+all" else it }
        val includes = terms.filter { it.lowercase().startsWith("include:") }.map { it.substringAfter(':') }
        return Spf(rec, all, includes, spfs.size > 1)
    }

    data class Dmarc(val record: String?, val policy: String?, val rua: String?, val pct: Int?) {
        val summary: String get() = when {
            record == null -> "No DMARC record"
            policy == null -> "DMARC record without p= (invalid)"
            else -> "DMARC p=$policy" + (pct?.let { " pct=$it" } ?: "")
        }
    }

    fun parseDmarc(txtRecords: List<String>): Dmarc {
        val rec = txtRecords.firstOrNull { it.trim().lowercase().startsWith("v=dmarc1") }
            ?: return Dmarc(null, null, null, null)
        val tags = rec.split(';').mapNotNull { part ->
            val kv = part.trim().split('=', limit = 2)
            if (kv.size == 2) kv[0].trim().lowercase() to kv[1].trim() else null
        }.toMap()
        return Dmarc(rec, tags["p"]?.lowercase(), tags["rua"], tags["pct"]?.toIntOrNull())
    }

    data class Mx(val priority: Int, val host: String)

    /** DoH MX data is `"10 mail.example.com."`. Sorted by priority. */
    fun parseMx(data: List<String>): List<Mx> = data.mapNotNull { d ->
        val parts = d.trim().split(Regex("\\s+"))
        if (parts.size >= 2) parts[0].toIntOrNull()?.let { Mx(it, parts[1].trimEnd('.')) } else null
    }.sortedBy { it.priority }

    /** Guess the mail provider from MX hosts (hint only). */
    fun mailProvider(mx: List<Mx>): String? {
        val hosts = mx.joinToString(" ") { it.host.lowercase() }
        return when {
            "google.com" in hosts || "googlemail.com" in hosts -> "Google Workspace / Gmail"
            "outlook.com" in hosts || "protection.outlook" in hosts -> "Microsoft 365 / Outlook"
            "protonmail" in hosts || "proton.me" in hosts -> "Proton Mail"
            "zoho" in hosts -> "Zoho Mail"
            "yahoodns" in hosts -> "Yahoo"
            "icloud.com" in hosts -> "iCloud"
            "mimecast" in hosts -> "Mimecast (filtering)"
            "pphosted" in hosts -> "Proofpoint (filtering)"
            mx.isEmpty() -> null
            else -> "Self-hosted / other"
        }
    }

    fun pivots(email: String): List<Pivot> {
        val e = normalize(email)
        val q = DorkComposer::encode
        val user = e.substringBefore('@')
        val domain = e.substringAfter('@')
        return listOf(
            Pivot("Google: exact address", "https://www.google.com/search?q=" + q("\"$e\"")),
            Pivot("Bing: exact address", "https://www.bing.com/search?q=" + q("\"$e\"")),
            Pivot("Paste sites", "https://www.google.com/search?q=" + q("\"$e\" (site:pastebin.com OR site:ghostbin.com OR site:justpaste.it)")),
            Pivot("GitHub (commits/code)", "https://github.com/search?type=commits&q=" + q("author-email:$e")),
            Pivot("Gravatar profile", gravatarProfileUrl(e)),
            Pivot("Have I Been Pwned (website)", "https://haveibeenpwned.com/account/" + q(e)),
            Pivot("Username search for '$user'", "https://www.google.com/search?q=" + q("\"$user\" -site:$domain")),
            Pivot("Domain site", "https://$domain"),
        )
    }

    internal fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
}

/**
 * Disposable / throwaway email domains. Small bundled list compiled for this app
 * (well-known public services). It is a hint, not proof; the user can refresh a larger
 * list at runtime from the public-domain (CC0) disposable-email-domains project.
 */
object DisposableDomains {
    const val REMOTE_LIST_URL =
        "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/main/disposable_email_blocklist.conf"

    val bundled: Set<String> = setOf(
        "10minutemail.com", "10minutemail.net", "20minutemail.com", "33mail.com", "anonaddy.me",
        "burnermail.io", "discard.email", "dispostable.com", "emailondeck.com", "fakeinbox.com",
        "getairmail.com", "getnada.com", "guerrillamail.com", "guerrillamail.net", "guerrillamail.org",
        "guerrillamailblock.com", "harakirimail.com", "inboxkitten.com", "jetable.org", "mailcatch.com",
        "maildrop.cc", "mailinator.com", "mailinator.net", "mailnesia.com", "mailpoof.com",
        "mintemail.com", "moakt.com", "mohmal.com", "mytemp.email", "nada.email", "sharklasers.com",
        "spam4.me", "spamgourmet.com", "temp-mail.io", "temp-mail.org", "tempail.com", "tempmail.com",
        "tempmail.dev", "tempmailo.com", "tempr.email", "throwawaymail.com", "trashmail.com",
        "trashmail.de", "yopmail.com", "yopmail.fr", "yopmail.net", "mail.tm", "emailfake.com",
    )

    /** Parse a one-domain-per-line list (comments with #). */
    fun parseList(text: String): Set<String> = text.lineSequence()
        .map { it.substringBefore('#').trim().lowercase() }
        .filter { it.isNotEmpty() && it.contains('.') }
        .toSet()

    /** True when [domain] or any parent domain is listed. */
    fun isDisposable(domain: String, extra: Set<String> = emptySet()): Boolean {
        var d = domain.trim().lowercase().trimEnd('.')
        while (d.contains('.')) {
            if (d in bundled || d in extra) return true
            d = d.substringAfter('.')
        }
        return false
    }
}
