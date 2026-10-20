package com.victorious.browser.antiphish

/**
 * Bundled, static, brand-neutral "spot the phish" quiz items for the training UI.
 * Examples use fictional services (AcmePay, NorthBank, CloudBox) — no real brands.
 */
object PhishQuiz {

    data class Choice(val text: String, val isPhish: Boolean, val explanation: String)

    data class Item(
        val id: String,
        val prompt: String,
        val scenario: String,
        val choices: List<Choice>,
    )

    val ITEMS: List<Item> = listOf(
        Item(
            id = "lookalike",
            prompt = "Which URL is the phishing lookalike?",
            scenario = "You expect a login email from AcmePay (acmepay.com).",
            choices = listOf(
                Choice(
                    "https://acmepay.com/login",
                    isPhish = false,
                    explanation = "Exact brand domain over HTTPS.",
                ),
                Choice(
                    "https://acmepay-secure.top/login",
                    isPhish = true,
                    explanation = "Brand embedded in a different registrable domain with a suspicious TLD.",
                ),
                Choice(
                    "https://accounts.acmepay.com/signin",
                    isPhish = false,
                    explanation = "Legitimate subdomain of the brand domain.",
                ),
            ),
        ),
        Item(
            id = "homoglyph",
            prompt = "Which host uses a homoglyph / punycode trick?",
            scenario = "Comparing three 'NorthBank' login hosts.",
            choices = listOf(
                Choice(
                    "northbank.com",
                    isPhish = false,
                    explanation = "Plain ASCII brand domain.",
                ),
                Choice(
                    "xn--northbnk-65a.com (decodes with a lookalike character)",
                    isPhish = true,
                    explanation = "Punycode/IDN host that visually mimics the brand.",
                ),
                Choice(
                    "login.northbank.com",
                    isPhish = false,
                    explanation = "Subdomain of the real registrable domain.",
                ),
            ),
        ),
        Item(
            id = "insecure_form",
            prompt = "Which page is unsafe to type a password into?",
            scenario = "You landed on a password form. Check the transport.",
            choices = listOf(
                Choice(
                    "https://cloudbox.example/account — password form",
                    isPhish = false,
                    explanation = "HTTPS protects the credential in transit.",
                ),
                Choice(
                    "http://192.168.9.44/login — password form",
                    isPhish = true,
                    explanation = "Password field on a non-HTTPS IP-literal host — classic credential phish.",
                ),
                Choice(
                    "https://help.cloudbox.example/docs",
                    isPhish = false,
                    explanation = "Docs page with no credential form.",
                ),
            ),
        ),
        Item(
            id = "cross_domain",
            prompt = "Which form action is a red flag?",
            scenario = "Page host is portal.northbank.example.",
            choices = listOf(
                Choice(
                    "action=https://portal.northbank.example/auth",
                    isPhish = false,
                    explanation = "Posts back to the same registrable domain.",
                ),
                Choice(
                    "action=https://auth-collector.xyz/ingest",
                    isPhish = true,
                    explanation = "Credential form posts to an unrelated domain — harvest indicator.",
                ),
                Choice(
                    "action=/local/login (relative)",
                    isPhish = false,
                    explanation = "Relative action stays on the page origin.",
                ),
            ),
        ),
        Item(
            id = "urgency",
            prompt = "Which message uses phishing urgency cues?",
            scenario = "Inbox triage — pick the suspicious wording.",
            choices = listOf(
                Choice(
                    "Your monthly CloudBox statement is ready. View in the app.",
                    isPhish = false,
                    explanation = "Routine notice, no threat language.",
                ),
                Choice(
                    "URGENT: account locked in 15 minutes — verify NOW or lose access forever!",
                    isPhish = true,
                    explanation = "Artificial urgency + fear + demand to 'verify' is a classic lure.",
                ),
                Choice(
                    "Reminder: password expires in 30 days. Change it in Settings when convenient.",
                    isPhish = false,
                    explanation = "Reasonable timeframe, user-controlled action.",
                ),
            ),
        ),
    )
}
