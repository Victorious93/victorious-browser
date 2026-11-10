package com.victorious.browser.osint.research

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DohAndRdapTest {
    @Test fun dohUrlAndParse() {
        assertEquals("https://cloudflare-dns.com/dns-query?name=example.com&type=MX", Doh.queryUrl(Doh.Provider.CLOUDFLARE, "example.com.", "mx"))
        assertEquals("https://dns.google/resolve?name=a.b&type=TXT", Doh.queryUrl(Doh.Provider.GOOGLE, "a.b", "TXT"))
        val json = """{"Status":0,"TC":false,"Question":[{"name":"example.com.","type":16}],
            "Answer":[{"name":"example.com.","type":16,"TTL":300,"data":"\"v=spf1 \" \"-all\""},
                      {"name":"example.com.","type":1,"TTL":60,"data":"93.184.216.34"},
                      {"name":"example.com.","type":99,"TTL":60,"data":"x"}]}"""
        val a = Doh.parse(json)
        assertTrue(a.ok)
        assertEquals("v=spf1 -all", a.records[0].data)
        assertEquals("TXT", a.records[0].type)
        assertEquals("example.com", a.records[0].name)
        assertEquals("A", a.records[1].type)
        assertEquals("TYPE99", a.records[2].type)
        val nx = Doh.parse("""{"Status":3}""")
        assertEquals("NXDOMAIN", nx.statusText)
        assertTrue(nx.records.isEmpty())
    }

    @Test fun names() {
        assertTrue(Doh.isValidName("_dmarc.example.com"))
        assertTrue(Doh.isValidName("4.3.2.1.in-addr.arpa"))
        assertFalse(Doh.isValidName("bad name.com"))
        assertFalse(Doh.isValidName("-x.com"))
        assertEquals("34.216.184.93.in-addr.arpa", Doh.ptrName("93.184.216.34"))
        assertNull(Doh.ptrName("300.1.1.1"))
    }

    @Test fun rdapDomain() {
        val json = """{"objectClassName":"domain","handle":"2336799_DOMAIN_COM-VRSN","ldhName":"EXAMPLE.COM",
          "status":["client delete prohibited","client transfer prohibited"],
          "events":[{"eventAction":"registration","eventDate":"1995-08-14T04:00:00Z"},{"eventAction":"expiration","eventDate":"2026-08-13T04:00:00Z"}],
          "nameservers":[{"objectClassName":"nameserver","ldhName":"A.IANA-SERVERS.NET"}],
          "entities":[{"objectClassName":"entity","roles":["registrar"],"vcardArray":["vcard",[["version",{},"text","4.0"],["fn",{},"text","RESERVED-Internet Assigned Numbers Authority"]]],
             "entities":[{"roles":["abuse"],"vcardArray":["vcard",[["fn",{},"text","Abuse Desk"]]]}]}]}"""
        val i = Rdap.parse(json)
        assertEquals("domain", i.kind)
        assertEquals("EXAMPLE.COM", i.name)
        assertEquals("1995-08-14T04:00:00Z", i.event("registration"))
        assertEquals(listOf("a.iana-servers.net"), i.nameservers)
        assertEquals("registrar" to "RESERVED-Internet Assigned Numbers Authority", i.entities[0])
        assertEquals("abuse" to "Abuse Desk", i.entities[1])
        assertNull(i.range)
    }

    @Test fun rdapIp() {
        val i = Rdap.parse("""{"objectClassName":"ip network","handle":"NET-8-8-8-0-2","startAddress":"8.8.8.0","endAddress":"8.8.8.255","name":"GOGL","country":"US"}""")
        assertEquals("8.8.8.0 – 8.8.8.255", i.range)
        assertEquals("GOGL", i.name)
        assertEquals("US", i.country)
        assertEquals("https://rdap.org/ip/8.8.8.8", Rdap.ipUrl(" 8.8.8.8"))
        assertEquals("https://rdap.org/domain/example.com", Rdap.domainUrl("Example.com."))
    }

    @Test fun crtSh() {
        assertEquals("https://crt.sh/?q=%25.example.com&output=json", CrtSh.url("example.com"))
        val json = """[{"common_name":"example.com","name_value":"*.example.com\nwww.example.com"},
            {"common_name":"mail.example.com","name_value":"mail.example.com\nevil.com"}]"""
        assertEquals(listOf("example.com", "mail.example.com", "www.example.com"), CrtSh.parseSubdomains(json, "example.com"))
        val truncated = """[{"name_value":"a.example.com"},{"name_value":"b.example.com\nc.example.com"},{"name_va"""
        assertEquals(listOf("a.example.com", "b.example.com", "c.example.com"), CrtSh.parseSubdomains(truncated, "example.com"))
    }

    @Test fun ipDetectionAndPivots() {
        assertTrue(InfraPivots.isIpv4("8.8.8.8"))
        assertFalse(InfraPivots.isIpv4("8.8.8"))
        assertTrue(InfraPivots.isIpv6("2001:4860:4860::8888"))
        assertFalse(InfraPivots.isIpv6("example.com"))
        assertTrue(InfraPivots.forIp("8.8.8.8").all { it.url.startsWith("https://") })
        assertTrue(InfraPivots.forDomain("example.com").any { it.url.contains("crt.sh") })
    }

    @Test fun hibp() {
        assertEquals(Hibp.Outcome.NotFound, Hibp.interpret(404, ""))
        val b = Hibp.interpret(200, """[{"Name":"Adobe","Domain":"adobe.com","BreachDate":"2013-10-04","DataClasses":["Email addresses","Passwords"]}]""")
        b as Hibp.Outcome.Breached
        assertEquals("Adobe", b.breaches[0].name)
        assertEquals(listOf("Email addresses", "Passwords"), b.breaches[0].dataClasses)
        assertTrue(Hibp.interpret(401, "") is Hibp.Outcome.Failed)
        assertTrue(Hibp.interpret(429, "") is Hibp.Outcome.Failed)
        assertEquals("https://haveibeenpwned.com/api/v3/breachedaccount/a%40b.co?truncateResponse=false", Hibp.breachUrl("a@b.co"))
        assertEquals("k", Hibp.headers(" k ")["hibp-api-key"])
    }
}
