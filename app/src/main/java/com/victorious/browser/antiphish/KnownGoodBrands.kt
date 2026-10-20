package com.victorious.browser.antiphish

/**
 * Bundled default known-good brands / domains (generic common services).
 * Users can edit via [AntiPhishSettings]; this list is the factory default only.
 *
 * Deliberately brand-generic service names — educational / protective, not a
 * live threat feed. No credentials, no API keys.
 */
object KnownGoodBrands {

    /** Default known-good registrable domains. */
    val DEFAULT_DOMAINS: List<String> = listOf(
        "google.com",
        "gmail.com",
        "youtube.com",
        "microsoft.com",
        "live.com",
        "outlook.com",
        "office.com",
        "apple.com",
        "icloud.com",
        "amazon.com",
        "aws.amazon.com",
        "facebook.com",
        "meta.com",
        "instagram.com",
        "twitter.com",
        "x.com",
        "linkedin.com",
        "github.com",
        "gitlab.com",
        "paypal.com",
        "stripe.com",
        "dropbox.com",
        "slack.com",
        "zoom.us",
        "netflix.com",
        "spotify.com",
        "reddit.com",
        "wikipedia.org",
        "cloudflare.com",
        "proton.me",
        "protonmail.com",
        "yahoo.com",
        "ebay.com",
        "walmart.com",
        "target.com",
        "bankofamerica.com",
        "wellsfargo.com",
        "chase.com",
        "citibank.com",
        "americanexpress.com",
        "discover.com",
    )

    /**
     * Keyword → canonical domain. Used by [PageBrandAnalyzer] for title/logo
     * text mismatch checks. Keep keywords distinctive (≥4 chars preferred).
     */
    val DEFAULT_KEYWORDS: Map<String, String> = mapOf(
        "google" to "google.com",
        "gmail" to "gmail.com",
        "youtube" to "youtube.com",
        "microsoft" to "microsoft.com",
        "outlook" to "outlook.com",
        "office 365" to "office.com",
        "office365" to "office.com",
        "icloud" to "icloud.com",
        "apple id" to "apple.com",
        "amazon" to "amazon.com",
        "aws console" to "aws.amazon.com",
        "facebook" to "facebook.com",
        "instagram" to "instagram.com",
        "linkedin" to "linkedin.com",
        "github" to "github.com",
        "paypal" to "paypal.com",
        "dropbox" to "dropbox.com",
        "netflix" to "netflix.com",
        "spotify" to "spotify.com",
        "cloudflare" to "cloudflare.com",
        "protonmail" to "protonmail.com",
        "wellsfargo" to "wellsfargo.com",
        "bank of america" to "bankofamerica.com",
        "american express" to "americanexpress.com",
        "chase bank" to "chase.com",
    )
}
