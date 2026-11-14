package com.victorious.browser.victorlinux

import org.junit.Assert.*
import org.junit.Test

class OriginAndKeysTest {
    private fun ok(raw: String, tunnel: Boolean = false) = OriginPolicy.parse(raw, tunnel) as OriginPolicy.Result.Ok
    private fun rejected(raw: String, tunnel: Boolean = false) = (OriginPolicy.parse(raw, tunnel) as OriginPolicy.Result.Rejected).reason

    @Test fun phoneLoopbackIsNotThePc() {
        for (h in listOf("127.0.0.1:8080", "localhost", "https://127.0.0.1:8787", "[::1]:80", "127.9.9.9")) assertTrue(h, rejected(h).contains("this phone"))
    }

    @Test fun tunnelMustBeStatedExplicitlyAndWarns() {
        val r = ok("http://127.0.0.1:8080", tunnel = true)
        assertTrue(r.warnings.any { it.contains("tunnel") })
        assertTrue(r.warnings.any { it.contains("Unencrypted") })
    }

    @Test fun httpOnlyOnPrivateIpLiterals() {
        for (h in listOf("http://192.168.1.20:8080", "http://10.0.0.5", "http://172.16.4.4:81", "http://100.64.0.9:8080", "http://169.254.1.1"))
            assertTrue(h, ok(h).warnings.any { it.contains("Unencrypted") })
        for (h in listOf("http://8.8.8.8", "http://pc.example.com", "http://172.32.0.1", "http://100.128.0.1")) assertTrue(h, rejected(h).contains("Plain http"))
    }

    @Test fun httpsAcceptedAndNormalised() {
        assertEquals("https://192.168.1.20:8080", ok("192.168.1.20:8080").origin)
        assertEquals("https://pc.example.com", ok("HTTPS://PC.Example.com:443/").origin)
        assertEquals("https://[fd00::1]:9443", ok("https://[fd00::1]:9443").origin)
    }

    @Test fun addressesCarryNoSecretsOrPaths() {
        assertTrue(rejected("https://user:pw@192.168.1.2").contains("username"))
        assertTrue(rejected("https://192.168.1.2/?key=abc").contains("without a path"))
        assertTrue(rejected("https://192.168.1.2/v1").contains("without a path"))
        assertTrue(rejected("ftp://192.168.1.2").contains("http"))
        assertTrue(rejected("").isNotEmpty())
    }

    @Test fun sameOriginIsExact() {
        val o = "https://192.168.1.20:8080"
        assertTrue(OriginPolicy.sameOrigin(o, "https://192.168.1.20:8080/v1/chat/completions"))
        assertFalse(OriginPolicy.sameOrigin(o, "https://192.168.1.20:8081/x"))
        assertFalse(OriginPolicy.sameOrigin(o, "http://192.168.1.20:8080/x"))
        assertFalse(OriginPolicy.sameOrigin(o, "https://192.168.1.21:8080/x"))
        assertFalse(OriginPolicy.sameOrigin(o, "https://evil.example.com/x"))
    }

    private class Mem : SecretStore {
        val m = mutableMapOf<String, String>()
        override fun put(name: String, value: String) { m[name] = value }
        override fun get(name: String) = m[name]
        override fun remove(name: String) { m.remove(name) }
    }

    @Test fun keyIsBoundToItsOriginAndRevocable() {
        val keys = OriginBoundKeys(Mem())
        keys.save("p1", "https://192.168.1.20:8080", " victor-abc ")
        assertEquals("victor-abc", keys.get("p1", "https://192.168.1.20:8080"))
        assertNull("editing the address must not release the old key", keys.get("p1", "https://192.168.1.99:8080"))
        assertNull(keys.get("p2", "https://192.168.1.20:8080"))
        keys.revoke("p1"); assertFalse(keys.has("p1", "https://192.168.1.20:8080"))
        assertThrows(IllegalArgumentException::class.java) { keys.save("p1", "https://x", "a\nb") }
    }

    @Test fun exportsNeverContainKeysAndImportRevalidatesOrigins() {
        val store = Mem(); val keys = OriginBoundKeys(store)
        val p = VictorProfile("id1", "Desk PC", "https://192.168.1.20:8080", "a".repeat(64), "pc-1")
        keys.save(p.id, p.origin, "victor-SECRETKEY")
        val json = ProfileExport.toJson(listOf(p))
        assertFalse(json.contains("SECRETKEY"))
        assertTrue(json.contains("192.168.1.20"))
        val back = ProfileExport.fromJson(json)
        assertEquals(p.copy(lastModel = null), back.single())
        // a hostile import cannot smuggle in a loopback/plain-http origin or a bad pin
        val evil = """[{"id":"x","name":"n","origin":"http://evil.example.com","pinnedCertSha256":"zz"},{"id":"y","name":"n","origin":"https://127.0.0.1:1"},
            {"id":"z","name":"ok","origin":"https://pc.example.com","pinnedCertSha256":"nothex"}]"""
        val imported = ProfileExport.fromJson(evil)
        assertEquals(listOf("pc.example.com"), imported.map { it.origin.removePrefix("https://") })
        assertNull(imported.single().pinnedCertSha256)
    }

    @Test fun fingerprintNormalisation() {
        val raw = "AB:CD:" + "00".repeat(30)
        assertTrue(Fingerprint.isValid(raw))
        assertEquals(64, Fingerprint.normalize(raw).length)
        assertFalse(Fingerprint.isValid("abc")); assertFalse(Fingerprint.isValid(null))
    }
}
