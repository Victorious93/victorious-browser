package com.victorious.browser.antiphish

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormAnalyzerTest {

    @Test
    fun insecurePassword_onHttp() {
        val forms = listOf(PageFormSnapshot(hasPasswordField = true, hasUsernameField = true))
        val findings = FormAnalyzer.analyze("http://example.com/login", https = false, forms)
        assertTrue(findings.any { it.code == "insecure_password" })
    }

    @Test
    fun securePassword_onHttps_noInsecureFinding() {
        val forms = listOf(PageFormSnapshot(hasPasswordField = true))
        val findings = FormAnalyzer.analyze("https://example.com/login", https = true, forms)
        assertFalse(findings.any { it.code == "insecure_password" })
    }

    @Test
    fun crossDomainFormAction() {
        val forms = listOf(
            PageFormSnapshot(
                actionUrl = "https://evil-collector.xyz/ingest",
                actionHost = "evil-collector.xyz",
                hasPasswordField = true,
                hasUsernameField = true,
            ),
        )
        val findings = FormAnalyzer.analyze("https://portal.northbank.example/login", https = true, forms)
        assertTrue(findings.any { it.code == "cross_domain_action" })
    }

    @Test
    fun sameDomainAction_ok() {
        val forms = listOf(
            PageFormSnapshot(
                actionUrl = "https://portal.northbank.example/auth",
                actionHost = "portal.northbank.example",
                hasPasswordField = true,
            ),
        )
        val findings = FormAnalyzer.analyze("https://portal.northbank.example/login", https = true, forms)
        assertFalse(findings.any { it.code == "cross_domain_action" })
    }

    @Test
    fun ipLiteralCredentialForm() {
        val forms = listOf(PageFormSnapshot(hasPasswordField = true))
        val findings = FormAnalyzer.analyze("http://192.168.9.44/login", https = false, forms)
        assertTrue(findings.any { it.code == "ip_literal_creds" })
        assertTrue(findings.any { it.code == "insecure_password" })
    }

    @Test
    fun hiddenCredentialFields() {
        val forms = listOf(
            PageFormSnapshot(
                hasPasswordField = true,
                hasHiddenCredentialField = true,
            ),
        )
        val findings = FormAnalyzer.analyze("https://example.com/x", https = true, forms)
        assertTrue(findings.any { it.code == "hidden_creds" })
    }

    @Test
    fun hostOf_stripsUrl() {
        assertTrue(FormAnalyzer.hostOf("https://user@Example.COM:8443/a?x=1#f") == "Example.COM" ||
            FormAnalyzer.hostOf("https://Example.COM:8443/a?x=1")?.equals("Example.COM", true) == true)
    }
}
