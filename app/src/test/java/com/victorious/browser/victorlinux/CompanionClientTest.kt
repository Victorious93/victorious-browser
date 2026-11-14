package com.victorious.browser.victorlinux

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class CompanionClientTest {
    private val servers = mutableListOf<() -> Unit>()
    @After fun stop() { servers.forEach { it() } }

    private val goodReport = """{"schema":1,"pc":{"id":"11111111-1111-1111-1111-111111111111","name":"desk"},
        "hardware":{"ram_total":34359738368,"ram_available":20000000000,"gpus":[{"vendor":"amd","name":"RX 6800 XT","vram_total":17163091968}],
        "backend":"vulkan","memory_pool":"dedicated","memory_budget":14000000000,"threads_default":7},
        "runtime":{"running":true,"model":"qwen.gguf","backend":"vulkan","context":8192,"gpu_layers":999,"generation_check":{"state":"verified"}},
        "models":[{"name":"qwen.gguf","fit":"fits","ctx":8192,"verified":"verified-here","size":5000000000}],
        "transport":{"tls":true,"tls_fingerprint_sha256":null}}"""

    private fun handler(key: String = "good", report: String = goodReport, status: Int = 200): (HttpExchange) -> Unit = { ex ->
        val auth = ex.requestHeaders.getFirst("Authorization")
        val (code, body) = when {
            ex.requestURI.path == "/victor/companion" && auth != "Bearer $key" -> 401 to "{}"
            ex.requestURI.path == "/victor/companion" -> status to report
            ex.requestURI.path == "/v1/chat/completions" && auth != "Bearer $key" -> 401 to "{}"
            ex.requestURI.path == "/v1/chat/completions" -> 200 to """{"choices":[{"message":{"role":"assistant","content":"hello from the PC"}}]}"""
            ex.requestURI.path == "/redir" -> 302 to ""
            else -> 404 to "{}"
        }
        val bytes = body.toByteArray()
        if (ex.requestURI.path == "/victor/companion" && code == 200 && status == 302) ex.responseHeaders.add("Location", "http://evil.example.com/")
        ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
        ex.close()
    }

    private fun http(h: (HttpExchange) -> Unit = handler()): Int {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { h(it) }; s.start(); servers += { s.stop(0) }
        return s.address.port
    }

    private fun profile(port: Int, https: Boolean = false, pin: String? = null, pcId: String? = null, last: String? = null) =
        VictorProfile("p", "pc", "${if (https) "https" else "http"}://127.0.0.1:$port", pin, pcId, last, tunnel = true)

    private fun client(p: VictorProfile, key: String? = "good") = CompanionClient(p, { key }, 2_000, 5_000)

    @Test fun onlineReportParsesHardwareAndModelFit() {
        val st = client(profile(http())).fetchStatus() as CompanionState.Online
        val r = st.report
        assertEquals("RX 6800 XT", r.gpus.single()); assertEquals("vulkan", r.backend); assertEquals("dedicated", r.memoryPool)
        assertEquals("verified", r.generationCheck); assertEquals(8192, r.context)
        assertEquals("verified-here", r.models.single().verified)
    }

    @Test fun badKeyIsReportedAsBadKey() {
        assertEquals(CompanionState.BadKey, client(profile(http()), key = "rotated").fetchStatus())
        assertEquals(CompanionState.BadKey, client(profile(http()), key = null).fetchStatus())
    }

    @Test fun tunnelDownIsActionable() {
        val port = http().also { stop() }              // port closed again
        val st = client(profile(port)).fetchStatus()
        assertTrue(st is CompanionState.Offline)
        assertTrue((st as CompanionState.Offline).reason.contains("tunnel"))
    }

    @Test fun changedPcIsDetectedAndModelChangeNoted() {
        val port = http()
        val changed = client(profile(port, pcId = "99999999-9999-9999-9999-999999999999")).fetchStatus()
        assertTrue(changed is CompanionState.ChangedPc)
        assertEquals("11111111-1111-1111-1111-111111111111", (changed as CompanionState.ChangedPc).now)
        val same = client(profile(port, pcId = "11111111-1111-1111-1111-111111111111", last = "old-model.gguf")).fetchStatus() as CompanionState.Online
        assertTrue(same.notes.any { it.contains("old-model.gguf") && it.contains("qwen.gguf") })
    }

    @Test fun portChangeIsJustAnotherOfflineUntilEdited() {
        val live = http()
        assertTrue(client(profile(live)).fetchStatus() is CompanionState.Online)
        assertTrue(client(profile(live + 1)).fetchStatus() is CompanionState.Offline)
    }

    @Test fun redirectsAreNeverFollowedAndNeverCarryTheKey() {
        val port = http(handler(status = 302))
        val st = client(profile(port)).fetchStatus()
        assertTrue(st is CompanionState.WrongHost)
    }

    @Test fun unsupportedSchemaAndNonGatewayAreExplained() {
        assertTrue(client(profile(http(handler(report = """{"schema":2,"pc":{"id":"x"}}""")))).fetchStatus() is CompanionState.Unsupported)
        assertTrue(client(profile(http(handler(report = "<html>")))).fetchStatus() is CompanionState.Unsupported)
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { it.sendResponseHeaders(404, -1); it.close() }; s.start(); servers += { s.stop(0) }
        assertTrue(client(profile(s.address.port)).fetchStatus() is CompanionState.Unsupported)
    }

    @Test fun chatGoesOnlyToThePcAndReportsBadKey() {
        val port = http()
        assertEquals(CompanionClient.Chat.Reply("hello from the PC"), client(profile(port)).chat(listOf("user" to "hi")))
        val bad = client(profile(port), key = "nope").chat(listOf("user" to "hi")) as CompanionClient.Chat.Failed
        assertEquals(CompanionState.BadKey, bad.state)
    }

    // ---- TLS: scoped pin, no trust-all
    private data class Tls(val port: Int, val fingerprint: String)

    private fun cert(san: String): Triple<java.security.KeyPair, X509Certificate, String> {
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=victorlinux-test")
        val b = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()), Date(System.currentTimeMillis() - 60_000),
            Date(System.currentTimeMillis() + 3_600_000), name, kp.public)
        b.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        b.addExtension(Extension.subjectAlternativeName, false, GeneralNames(GeneralName(GeneralName.iPAddress, san)))
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(BouncyCastleProvider()).build(kp.private)
        val c = JcaX509CertificateConverter().setProvider(BouncyCastleProvider()).getCertificate(b.build(signer))
        return Triple(kp, c, PinnedTls.sha256(c))
    }

    private fun https(san: String = "127.0.0.1"): Tls {
        val (kp, c, fp) = cert(san)
        val ks = KeyStore.getInstance("PKCS12").apply { load(null, null); setKeyEntry("k", kp.private, "pw".toCharArray(), arrayOf(c)) }
        val kmf = KeyManagerFactory.getInstance("SunX509").apply { init(ks, "pw".toCharArray()) }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        val s = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.httpsConfigurator = HttpsConfigurator(ctx)
        s.createContext("/") { handler()(it) }; s.start(); servers += { s.stop(0) }
        return Tls(s.address.port, fp)
    }

    @Test fun selfSignedWithoutPinIsRejectedNotIgnored() {
        val t = https()
        val st = client(profile(t.port, https = true)).fetchStatus()
        assertTrue(st.toString(), st is CompanionState.WrongHost)
        assertTrue((st as CompanionState.WrongHost).reason.contains("never ignored"))
    }

    @Test fun correctPinAcceptsExactlyThatCertificate() {
        val t = https()
        assertTrue(client(profile(t.port, https = true, pin = t.fingerprint)).fetchStatus() is CompanionState.Online)
        assertTrue(client(profile(t.port, https = true, pin = t.fingerprint.uppercase().chunked(2).joinToString(":"))).fetchStatus() is CompanionState.Online)
    }

    @Test fun wrongPinIsRejected() {
        val t = https()
        val st = client(profile(t.port, https = true, pin = "0".repeat(64))).fetchStatus()
        assertTrue(st is CompanionState.WrongHost)
        assertTrue((st as CompanionState.WrongHost).reason.contains("does not match the fingerprint"))
    }

    @Test fun anotherPcAtTheSameAddressWithADifferentCertIsRejected() {
        val a = https(); val b = https()
        assertTrue(client(profile(b.port, https = true, pin = a.fingerprint)).fetchStatus() is CompanionState.WrongHost)
    }

    @Test fun hostNameMustStillMatchEvenWithAMatchingPin() {
        val t = https(san = "10.99.99.99")                      // certificate is for another address
        val st = client(profile(t.port, https = true, pin = t.fingerprint)).fetchStatus()
        assertTrue(st.toString(), st is CompanionState.WrongHost)
    }

    @Test fun theKeyIsNeverSentToAnotherOrigin() {
        val p = profile(http()).copy(origin = "http://127.0.0.1:1")
        // a hand-edited profile cannot make the client talk to a different origin than the one it checks
        assertTrue(client(p).fetchStatus() is CompanionState.Offline)
    }
}
