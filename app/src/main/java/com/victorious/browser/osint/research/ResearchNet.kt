package com.victorious.browser.osint.research

import android.content.Context
import com.victorious.browser.browser.privacy.Gpc
import com.victorious.browser.browser.privacy.PrivacyNav
import com.victorious.browser.browser.privacy.PrivacySettings
import com.victorious.browser.inspect.LocalProxyService
import com.victorious.browser.linkembed.BoundedFetcher
import com.victorious.browser.linkembed.FetchLimits
import com.victorious.browser.linkembed.FetchResult
import com.victorious.browser.linkembed.UrlConnectionTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * All Deep Research network calls go through the app's [BoundedFetcher]: http(s) only,
 * capped redirects, size caps, an overall deadline, no cookies, and routed through the
 * local inspection proxy when it is running (so they also appear in proxy history).
 */
class ResearchNet(private val context: Context) {

    private fun fetcher(maxBytes: Int, timeoutMs: Long = 15_000): BoundedFetcher {
        val privacy = PrivacySettings.get(context.applicationContext)
        val proxy = if (LocalProxyService.isRunning.value) {
            Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", LocalProxyService.port))
        } else null
        val strip = privacy.stripParams.value
        return BoundedFetcher(
            transport = UrlConnectionTransport(proxy),
            limits = FetchLimits(maxRedirects = 5, maxBytes = maxBytes, totalTimeoutMs = timeoutMs),
            // Force-HTTPS is not applied here: every endpoint we call is already https.
            rewrite = { url -> PrivacyNav.rewrite(url, stripTracking = strip, forceHttps = false).url },
        )
    }

    private fun baseHeaders(): Map<String, String> =
        mapOf("User-Agent" to USER_AGENT) + Gpc.headers(PrivacySettings.get(context.applicationContext).gpc.value)

    suspend fun get(url: String, maxBytes: Int = 512 * 1024, headers: Map<String, String> = emptyMap(), timeoutMs: Long = 15_000): FetchResult =
        withContext(Dispatchers.IO) { fetcher(maxBytes, timeoutMs).fetch(url, baseHeaders() + headers) }

    suspend fun dns(name: String, type: String, provider: Doh.Provider = Doh.Provider.CLOUDFLARE): Result<Doh.Answer> {
        if (!Doh.isValidName(name)) return Result.failure(IllegalArgumentException("Not a valid DNS name"))
        val r = get(Doh.queryUrl(provider, name, type), 64 * 1024, Doh.HEADERS)
        if (!r.ok || r.status != 200) return Result.failure(IllegalStateException(r.error ?: "HTTP ${r.status}"))
        return runCatching { Doh.parse(r.text()) }
    }

    suspend fun txt(name: String): List<String> = dns(name, "TXT").getOrNull()?.records?.filter { it.type == "TXT" }?.map { it.data }.orEmpty()

    suspend fun rdap(url: String): Result<Rdap.Info> {
        val r = get(url, 512 * 1024, mapOf("Accept" to "application/rdap+json, application/json"))
        if (r.status == 404) return Result.failure(IllegalStateException("Not found in RDAP (404)"))
        if (!r.ok || r.status != 200) return Result.failure(IllegalStateException(r.error ?: "HTTP ${r.status}"))
        return runCatching { Rdap.parse(r.text()) }
    }

    suspend fun crtSh(domain: String): Result<Pair<List<String>, Boolean>> {
        val r = get(CrtSh.url(domain), 4 * 1024 * 1024, mapOf("Accept" to "application/json"), timeoutMs = 30_000)
        if (r.status != 200 || (r.error != null && r.body.isEmpty())) return Result.failure(IllegalStateException(r.error ?: "HTTP ${r.status}"))
        return runCatching { CrtSh.parseSubdomains(r.text(), domain) to r.truncated }
    }

    /** true = avatar exists, false = 404 (none), null = unknown. */
    suspend fun gravatarExists(email: String): Boolean? {
        val r = get(EmailChecks.gravatarCheckUrl(email), 128 * 1024)
        return when (r.status) { 200 -> true; 404 -> false; else -> null }
    }

    suspend fun hibp(email: String, apiKey: String): Hibp.Outcome {
        val r = get(Hibp.breachUrl(email), 512 * 1024, Hibp.headers(apiKey))
        if (r.status == null) return Hibp.Outcome.Failed(r.error ?: "No response")
        return Hibp.interpret(r.status, r.text())
    }

    suspend fun disposableRemote(): Result<Set<String>> {
        val r = get(DisposableDomains.REMOTE_LIST_URL, 3 * 1024 * 1024, timeoutMs = 25_000)
        if (r.status != 200) return Result.failure(IllegalStateException(r.error ?: "HTTP ${r.status}"))
        return Result.success(DisposableDomains.parseList(r.text()))
    }

    suspend fun sherlockSites(): Result<List<UsernameSearch.Site>> {
        val r = get(UsernameSearch.SHERLOCK_DATA_URL, 2 * 1024 * 1024, timeoutMs = 25_000)
        if (r.status != 200 || r.truncated) return Result.failure(IllegalStateException(r.error ?: if (r.truncated) "List too large" else "HTTP ${r.status}"))
        return runCatching { UsernameSearch.parseSites(r.text()) }
    }

    fun bundledSites(): List<UsernameSearch.Site> = runCatching {
        context.assets.open("osint/username_sites.json").bufferedReader().use { UsernameSearch.parseSites(it.readText()) }
    }.getOrDefault(emptyList())

    /**
     * Check [sites] for [username] with at most [concurrency] requests in flight and a
     * small pause per request (rate limit). [onResult] is called as each check finishes.
     */
    suspend fun checkUsername(
        username: String,
        sites: List<UsernameSearch.Site>,
        concurrency: Int = 4,
        pauseMs: Long = 300,
        onResult: (UsernameSearch.Check) -> Unit,
    ) = coroutineScope {
        val gate = Semaphore(concurrency.coerceIn(1, 8))
        sites.map { site ->
            async {
                val profile = site.profileUrl(username)
                if (!site.accepts(username)) {
                    onResult(UsernameSearch.Check(site, UsernameSearch.Verdict.SKIPPED, profile, "Username format not allowed on this site"))
                    return@async
                }
                gate.withPermit {
                    val probe = site.probeUrl(username)
                    val r = get(probe, 256 * 1024, timeoutMs = 12_000)
                    val redirected = r.redirects.isNotEmpty() && r.finalUrl?.trimEnd('/') != probe.trimEnd('/')
                    val (v, detail) = UsernameSearch.evaluate(site, r.status, if (r.body.isEmpty()) "" else r.text(), redirected, r.error)
                    onResult(UsernameSearch.Check(site, v, profile, detail))
                    delay(pauseMs)
                }
            }
        }.awaitAll()
    }

    suspend fun fetchImage(url: String): Result<ByteArray> {
        val r = get(url, MAX_IMAGE_BYTES, mapOf("Accept" to "image/*"), timeoutMs = 25_000)
        if (r.status != 200) return Result.failure(IllegalStateException(r.error ?: "HTTP ${r.status}"))
        if (r.truncated) return Result.failure(IllegalStateException("Image larger than ${MAX_IMAGE_BYTES / (1024 * 1024)} MB"))
        return Result.success(r.body)
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android) VictoriousBrowser/0.2 DeepResearch"
        const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
