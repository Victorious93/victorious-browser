package com.victorious.browser.browser.privacy

import java.net.URI
import java.net.URLDecoder

/**
 * Pure, Android-free privacy-protection logic ("Privacy protections" / "Fire").
 * Inspired by the feature set of privacy-focused mobile browsers; no third-party
 * code, lists or branding are copied. Everything here is unit-tested.
 */

/** Host helpers shared by the protections. */
object HostUtil {
    /** Lower-cased host of [url], or null for non-http(s) / unparsable input. */
    fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val u = URI(url.trim())
            val s = u.scheme?.lowercase()
            if (s != "http" && s != "https") null else u.host?.lowercase()?.removeSuffix(".")
        } catch (_: Exception) {
            null
        }
    }

    /** Normalise user input ("https://www.Example.com/x", "example.com") to a bare host. */
    fun normalizeHost(input: String): String? {
        val t = input.trim().lowercase()
        if (t.isEmpty()) return null
        val h = if ("://" in t) hostOf(t) else t.substringBefore('/').substringBefore(':')
        val cleaned = h?.removePrefix("www.")?.removeSuffix(".") ?: return null
        if (cleaned.isEmpty() || cleaned.any { it.isWhitespace() || it == '*' }) return null
        return cleaned
    }

    /** True if [host] equals [suffix] or is a subdomain of it. */
    fun matchesSuffix(host: String, suffix: String): Boolean =
        host == suffix || host.endsWith(".$suffix")

    private val MULTI_PART_TLDS = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.jp", "co.nz",
        "com.br", "com.cn", "com.mx", "co.in", "co.za", "com.tr", "com.sg", "com.hk", "co.kr",
    )

    /**
     * Approximate registrable domain ("eTLD+1"). Uses a small multi-part-TLD table
     * rather than the full Public Suffix List — good enough for 1st/3rd-party splits,
     * not for security decisions. IP literals are returned unchanged.
     */
    fun baseDomain(host: String): String {
        val h = host.lowercase().removeSuffix(".")
        if (isIpLiteral(h)) return h
        val parts = h.split('.')
        if (parts.size <= 2) return h
        val lastTwo = parts.takeLast(2).joinToString(".")
        return if (lastTwo in MULTI_PART_TLDS) parts.takeLast(3).joinToString(".") else lastTwo
    }

    fun isThirdParty(requestHost: String, pageHost: String?): Boolean =
        pageHost != null && baseDomain(requestHost) != baseDomain(pageHost)

    fun isIpLiteral(host: String): Boolean =
        host.startsWith("[") || ':' in host || host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
}

/** Fireproof: hosts whose 1st-party cookies + storage survive the Fire button. */
object FireproofLogic {
    fun isFireproof(host: String?, fireproof: Set<String>): Boolean {
        val h = host?.lowercase()?.removeSuffix(".") ?: return false
        return fireproof.any { HostUtil.matchesSuffix(h, it) }
    }

    fun add(set: Set<String>, input: String): Set<String> =
        HostUtil.normalizeHost(input)?.let { set + it } ?: set

    fun remove(set: Set<String>, host: String): Set<String> = set - host.lowercase()
}

/** What a Fire run should touch. */
enum class FireScope { THIS_TAB, EVERYWHERE }

data class FireOptions(
    val scope: FireScope = FireScope.EVERYWHERE,
    val keepHistory: Boolean = false,
    val keepCookies: Boolean = false,
)

/** Keep-vs-burn decisions for a Fire run (pure; the Android burner executes them). */
object FirePlan {
    /** Should cookies/storage for [host] be burned? */
    fun burnSiteData(host: String?, fireproof: Set<String>, opts: FireOptions): Boolean =
        !opts.keepCookies && !FireproofLogic.isFireproof(host, fireproof)

    /** History entries (by URL) that survive this run. Bookmarks/downloads are never touched. */
    fun historyToKeep(urls: List<String>, fireproof: Set<String>, opts: FireOptions, tabHost: String? = null): List<String> {
        if (opts.keepHistory) return urls
        return urls.filter { u ->
            val h = HostUtil.hostOf(u)
            when (opts.scope) {
                // This-tab: only that site's history goes.
                FireScope.THIS_TAB -> tabHost == null || h == null || !HostUtil.matchesSuffix(h, tabHost)
                // Everywhere: fireproofed sites keep their history (same as their data).
                FireScope.EVERYWHERE -> FireproofLogic.isFireproof(h, fireproof)
            }
        }
    }

    /** WebStorage origins (scheme://host[:port]) to delete. */
    fun originsToBurn(origins: Collection<String>, fireproof: Set<String>, opts: FireOptions, tabHost: String? = null): List<String> =
        origins.filter { o ->
            val h = HostUtil.hostOf(o)
            val inScope = opts.scope == FireScope.EVERYWHERE || (tabHost != null && h != null && HostUtil.matchesSuffix(h, tabHost))
            inScope && burnSiteData(h, fireproof, opts)
        }
}

/** Link-tracking parameter stripping. */
object TrackingParams {
    val EXACT = setOf(
        "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid", "mc_eid", "mc_cid",
        "yclid", "twclid", "ttclid", "igshid", "li_fat_id", "_hsenc", "_hsmi", "mkt_tok",
        "oly_anon_id", "oly_enc_id", "vero_id", "rb_clickid", "s_cid", "ef_id", "_openstat",
        "srsltid", "epik", "ck_subscriber_id", "wickedid", "__s",
    )
    val PREFIXES = listOf("utm_", "hsa_", "pk_", "mtm_")

    fun isTrackingParam(name: String): Boolean {
        val n = name.lowercase()
        return n in EXACT || PREFIXES.any { n.startsWith(it) }
    }

    /** Returns [url] without tracking params, or the same string when nothing changed. */
    fun strip(url: String): String {
        val qStart = url.indexOf('?')
        if (qStart < 0) return url
        val hashStart = url.indexOf('#', qStart).let { if (it < 0) url.length else it }
        val query = url.substring(qStart + 1, hashStart)
        if (query.isEmpty()) return url
        val parts = query.split('&')
        val kept = parts.filter { p ->
            val rawName = p.substringBefore('=')
            val name = runCatching { URLDecoder.decode(rawName, "UTF-8") }.getOrDefault(rawName)
            !isTrackingParam(name)
        }
        if (kept.size == parts.size) return url
        val base = url.substring(0, qStart)
        val frag = url.substring(hashStart)
        return if (kept.isEmpty()) base + frag else base + "?" + kept.joinToString("&") + frag
    }
}

/** Force-HTTPS decision. */
object HttpsUpgrade {
    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".internal", ".home", ".localdomain", ".test", ".onion", ".ts.net")

    /** Returns the https:// URL to load instead, or null when no upgrade should happen. */
    fun upgrade(url: String, failedHosts: Set<String> = emptySet()): String? {
        val u = try { URI(url.trim()) } catch (_: Exception) { return null }
        if (u.scheme?.lowercase() != "http") return null
        val host = u.host?.lowercase() ?: return null
        if (!isUpgradeable(host, u.port)) return null
        if (host in failedHosts) return null
        val rest = url.trim().substring(url.trim().indexOf("://"))
        return "https$rest"
    }

    /** Skip IPs, localhost, single-label and LAN/internal names, and explicit non-80 ports (recon targets). */
    fun isUpgradeable(host: String, port: Int = -1): Boolean {
        if (port != -1 && port != 80) return false
        if (HostUtil.isIpLiteral(host)) return false
        if (host == "localhost" || host.endsWith(".localhost")) return false
        if ('.' !in host) return false
        if (LOCAL_SUFFIXES.any { host.endsWith(it) }) return false
        return true
    }
}

/** Global Privacy Control. */
object Gpc {
    const val HEADER = "Sec-GPC"
    const val VALUE = "1"

    fun headers(enabled: Boolean): Map<String, String> = if (enabled) mapOf(HEADER to VALUE) else emptyMap()

    /** Document-start JS exposing navigator.globalPrivacyControl = true. Idempotent. */
    fun script(): String = """
        (function(){try{
          if (navigator.globalPrivacyControl === true) return;
          Object.defineProperty(Navigator.prototype,'globalPrivacyControl',{get:function(){return true;},configurable:true,enumerable:true});
        }catch(e){}})();
    """.trimIndent()
}

/** Cross-origin referrer trimming. */
object ReferrerTrim {
    /**
     * Referer to send when navigating from [fromUrl] to [toUrl]: unchanged for same-site,
     * origin-only for cross-site https→https, and none for https→http downgrades.
     */
    fun trim(fromUrl: String?, toUrl: String): String? {
        val fromHost = HostUtil.hostOf(fromUrl) ?: return null
        val toHost = HostUtil.hostOf(toUrl) ?: return null
        val fromScheme = fromUrl!!.trim().substringBefore("://").lowercase()
        val toScheme = toUrl.trim().substringBefore("://").lowercase()
        if (fromScheme == "https" && toScheme == "http") return null
        if (HostUtil.baseDomain(fromHost) == HostUtil.baseDomain(toHost)) return fromUrl
        return "$fromScheme://$fromHost/"
    }

    /** Document-start JS: force a strict policy so pages can't widen it with unsafe-url. */
    fun script(): String = """
        (function(){try{
          var m=document.createElement('meta');m.name='referrer';m.content='strict-origin-when-cross-origin';
          (document.head||document.documentElement).appendChild(m);
        }catch(e){}})();
    """.trimIndent()
}

/** Result of rewriting a top-level navigation. */
data class NavRewrite(val url: String, val strippedParams: Boolean, val httpsUpgraded: Boolean) {
    val changed: Boolean get() = strippedParams || httpsUpgraded
}

object PrivacyNav {
    fun rewrite(url: String, stripTracking: Boolean, forceHttps: Boolean, failedHttpsHosts: Set<String> = emptySet()): NavRewrite {
        var u = url
        val stripped = stripTracking && TrackingParams.strip(u).also { u = it } != url
        val upgraded = if (forceHttps) HttpsUpgrade.upgrade(u, failedHttpsHosts)?.also { u = it } != null else false
        return NavRewrite(u, stripped, upgraded)
    }
}

/** Bangs: "!g foo" → Google search for foo. Unknown bangs return null. */
object Bangs {
    private val MAP = mapOf(
        "g" to "https://www.google.com/search?q=",
        "w" to "https://en.wikipedia.org/w/index.php?search=",
        "b" to "https://www.bing.com/search?q=",
        "ddg" to "https://duckduckgo.com/?q=",
        "gh" to "https://github.com/search?q=",
        "yt" to "https://www.youtube.com/results?search_query=",
    )

    fun resolve(input: String): String? {
        val t = input.trim()
        val m = Regex("""^!(\w+)\s+(.+)$""").find(t) ?: Regex("""^(.+?)\s+!(\w+)$""").find(t)?.let { r ->
            return MAP[r.groupValues[2].lowercase()]?.let { it + java.net.URLEncoder.encode(r.groupValues[1].trim(), "UTF-8") }
        } ?: return null
        val prefix = MAP[m.groupValues[1].lowercase()] ?: return null
        return prefix + java.net.URLEncoder.encode(m.groupValues[2].trim(), "UTF-8")
    }
}
