package com.victorious.browser.osint

/**
 * Pure, Android-free passive tech fingerprinting.
 *
 * Given a page's response headers and (optionally) its HTML, infer likely
 * server / framework / CMS from a compact built-in signature set. Every output
 * is an explicit HEURISTIC GUESS — presence of a signature is suggestive, not
 * proof. No network, no bundled mega-database; operates only on data the caller
 * already captured (e.g. a CaptureStore entry).
 */
object TechFingerprint {

    enum class Category { SERVER, LANGUAGE, FRAMEWORK, CMS, CDN_WAF, ANALYTICS }

    enum class Confidence { LOW, MEDIUM, HIGH }

    /** One inferred technology with the evidence that triggered it. */
    data class Signal(
        val technology: String,
        val category: Category,
        val evidence: String,
        val confidence: Confidence,
    )

    data class Result(val signals: List<Signal>) {
        val isEmpty: Boolean get() = signals.isEmpty()
    }

    private data class Header(val name: String, val value: String)

    /**
     * Analyze [headers] (name/value pairs, case-insensitive names) and optional
     * [html]. Returns de-duplicated signals ordered by confidence then name.
     */
    fun analyze(headers: List<Pair<String, String>>, html: String? = null): Result {
        val hs = headers.map { Header(it.first.trim(), it.second.trim()) }
        val out = LinkedHashMap<String, Signal>()

        fun add(tech: String, cat: Category, evidence: String, conf: Confidence) {
            val existing = out[tech]
            if (existing == null || conf.ordinal > existing.confidence.ordinal) {
                out[tech] = Signal(tech, cat, evidence, conf)
            }
        }

        fun headerValue(name: String): String? =
            hs.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value

        // --- Server header -----------------------------------------------------
        headerValue("Server")?.let { server ->
            val s = server.lowercase()
            val ev = "Server: $server"
            when {
                "cloudflare" in s -> add("Cloudflare", Category.CDN_WAF, ev, Confidence.HIGH)
                "nginx" in s -> add("nginx", Category.SERVER, ev, Confidence.HIGH)
                "apache" in s -> add("Apache httpd", Category.SERVER, ev, Confidence.HIGH)
                "microsoft-iis" in s -> add("Microsoft IIS", Category.SERVER, ev, Confidence.HIGH)
                "litespeed" in s -> add("LiteSpeed", Category.SERVER, ev, Confidence.HIGH)
                "openresty" in s -> add("OpenResty (nginx)", Category.SERVER, ev, Confidence.HIGH)
                "gws" in s -> add("Google Web Server", Category.SERVER, ev, Confidence.MEDIUM)
            }
        }

        // --- X-Powered-By ------------------------------------------------------
        headerValue("X-Powered-By")?.let { xp ->
            val s = xp.lowercase()
            val ev = "X-Powered-By: $xp"
            when {
                "php" in s -> add("PHP", Category.LANGUAGE, ev, Confidence.HIGH)
                "asp.net" in s -> add("ASP.NET", Category.FRAMEWORK, ev, Confidence.HIGH)
                "express" in s -> add("Express (Node.js)", Category.FRAMEWORK, ev, Confidence.HIGH)
                "next.js" in s -> add("Next.js", Category.FRAMEWORK, ev, Confidence.HIGH)
                "servlet" in s -> add("Java Servlet", Category.FRAMEWORK, ev, Confidence.MEDIUM)
            }
        }

        // --- Other telltale headers -------------------------------------------
        headerValue("X-AspNet-Version")?.let { add("ASP.NET", Category.FRAMEWORK, "X-AspNet-Version: $it", Confidence.HIGH) }
        headerValue("X-AspNetMvc-Version")?.let { add("ASP.NET MVC", Category.FRAMEWORK, "X-AspNetMvc-Version: $it", Confidence.HIGH) }
        headerValue("X-Drupal-Cache")?.let { add("Drupal", Category.CMS, "X-Drupal-Cache: $it", Confidence.HIGH) }
        headerValue("X-Generator")?.let {
            val ev = "X-Generator: $it"
            when {
                it.contains("drupal", true) -> add("Drupal", Category.CMS, ev, Confidence.HIGH)
                it.contains("wordpress", true) -> add("WordPress", Category.CMS, ev, Confidence.HIGH)
                else -> add(it, Category.CMS, ev, Confidence.LOW)
            }
        }
        headerValue("X-Shopify-Stage")?.let { add("Shopify", Category.CMS, "X-Shopify-Stage: $it", Confidence.HIGH) }
        headerValue("CF-Ray")?.let { add("Cloudflare", Category.CDN_WAF, "CF-Ray: $it", Confidence.HIGH) }
        headerValue("X-Vercel-Id")?.let { add("Vercel", Category.CDN_WAF, "X-Vercel-Id present", Confidence.HIGH) }
        headerValue("X-Served-By")?.let {
            if (it.contains("cache", true) || it.contains("fastly", true)) {
                add("Fastly", Category.CDN_WAF, "X-Served-By: $it", Confidence.MEDIUM)
            }
        }
        headerValue("Via")?.let {
            if (it.contains("varnish", true)) add("Varnish", Category.CDN_WAF, "Via: $it", Confidence.MEDIUM)
        }

        // --- Set-Cookie names --------------------------------------------------
        hs.filter { it.name.equals("Set-Cookie", ignoreCase = true) }.forEach { c ->
            val cookieName = c.value.substringBefore('=').trim()
            val ev = "Set-Cookie: $cookieName"
            when {
                cookieName.equals("PHPSESSID", true) -> add("PHP", Category.LANGUAGE, ev, Confidence.HIGH)
                cookieName.equals("JSESSIONID", true) -> add("Java Servlet", Category.FRAMEWORK, ev, Confidence.HIGH)
                cookieName.equals("ASP.NET_SessionId", true) -> add("ASP.NET", Category.FRAMEWORK, ev, Confidence.HIGH)
                cookieName.equals("laravel_session", true) -> add("Laravel (PHP)", Category.FRAMEWORK, ev, Confidence.HIGH)
                cookieName.equals("csrftoken", true) -> add("Django", Category.FRAMEWORK, ev, Confidence.MEDIUM)
                cookieName.equals("sessionid", true) -> add("Django", Category.FRAMEWORK, ev, Confidence.LOW)
                cookieName.startsWith("wordpress_", true) || cookieName.startsWith("wp-", true) ->
                    add("WordPress", Category.CMS, ev, Confidence.HIGH)
                cookieName.startsWith("_shopify", true) -> add("Shopify", Category.CMS, ev, Confidence.HIGH)
                cookieName.equals("connect.sid", true) -> add("Express (Node.js)", Category.FRAMEWORK, ev, Confidence.MEDIUM)
            }
        }

        // --- HTML body signatures ---------------------------------------------
        html?.let { body ->
            val meta = metaGenerator(body)
            if (meta != null) {
                val ev = "meta generator: $meta"
                val m = meta.lowercase()
                when {
                    "wordpress" in m -> add("WordPress", Category.CMS, ev, Confidence.HIGH)
                    "drupal" in m -> add("Drupal", Category.CMS, ev, Confidence.HIGH)
                    "joomla" in m -> add("Joomla", Category.CMS, ev, Confidence.HIGH)
                    "wix" in m -> add("Wix", Category.CMS, ev, Confidence.HIGH)
                    "shopify" in m -> add("Shopify", Category.CMS, ev, Confidence.HIGH)
                    "ghost" in m -> add("Ghost", Category.CMS, ev, Confidence.HIGH)
                    "hugo" in m -> add("Hugo", Category.FRAMEWORK, ev, Confidence.HIGH)
                    "gatsby" in m -> add("Gatsby", Category.FRAMEWORK, ev, Confidence.HIGH)
                    else -> add(meta, Category.CMS, ev, Confidence.LOW)
                }
            }
            val lower = body.lowercase()
            if ("/wp-content/" in lower || "/wp-includes/" in lower || "/wp-json" in lower)
                add("WordPress", Category.CMS, "path marker: /wp-content/ or /wp-json", Confidence.MEDIUM)
            if ("/sites/default/files" in lower || "drupal.settings" in lower)
                add("Drupal", Category.CMS, "path marker: /sites/default/files", Confidence.MEDIUM)
            if ("/_next/" in lower || "__next_data__" in lower)
                add("Next.js", Category.FRAMEWORK, "marker: /_next/ or __NEXT_DATA__", Confidence.MEDIUM)
            if ("/_nuxt/" in lower || "__nuxt__" in lower)
                add("Nuxt.js", Category.FRAMEWORK, "marker: /_nuxt/ or __NUXT__", Confidence.MEDIUM)
            if ("ng-version=" in lower)
                add("Angular", Category.FRAMEWORK, "attribute: ng-version", Confidence.MEDIUM)
            if ("data-reactroot" in lower || "react.createelement" in lower)
                add("React", Category.FRAMEWORK, "marker: data-reactroot / React", Confidence.LOW)
            if ("cdn.shopify.com" in lower)
                add("Shopify", Category.CMS, "asset host: cdn.shopify.com", Confidence.MEDIUM)
            if ("www.googletagmanager.com" in lower || "gtag(" in lower)
                add("Google Tag Manager", Category.ANALYTICS, "script: googletagmanager / gtag", Confidence.MEDIUM)
        }

        val ordered = out.values.sortedWith(
            compareByDescending<Signal> { it.confidence.ordinal }.thenBy { it.technology.lowercase() },
        )
        return Result(ordered)
    }

    /** Extract the `content` of a `<meta name="generator" content="...">` tag. */
    fun metaGenerator(html: String): String? {
        val re = Regex(
            "<meta[^>]*name=[\"']generator[\"'][^>]*content=[\"']([^\"']*)[\"']",
            RegexOption.IGNORE_CASE,
        )
        re.find(html)?.groupValues?.get(1)?.let { if (it.isNotBlank()) return it.trim() }
        // Also handle content-before-name ordering.
        val re2 = Regex(
            "<meta[^>]*content=[\"']([^\"']*)[\"'][^>]*name=[\"']generator[\"']",
            RegexOption.IGNORE_CASE,
        )
        return re2.find(html)?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }
}
