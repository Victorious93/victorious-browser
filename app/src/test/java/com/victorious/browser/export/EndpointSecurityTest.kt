package com.victorious.browser.export

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSecurityTest {
    @Test fun privateHosts() {
        listOf("localhost", "127.0.0.1", "10.1.2.3", "172.16.0.1", "172.31.255.1", "192.168.1.5", "169.254.1.1",
            "100.64.0.1", "100.127.255.255", "node.ts.net", "nas.local", "box", "[::1]", "fd12::1", "fe80::1")
            .forEach { assertTrue(it, EndpointSecurity.isPrivateHost(it)) }
    }

    @Test fun publicHosts() {
        listOf("example.com", "8.8.8.8", "172.32.0.1", "172.15.0.1", "100.63.0.1", "100.128.0.1", "192.169.1.1",
            "300.1.1.1", "evil.local.example.com", "", null)
            .forEach { assertFalse(it ?: "null", EndpointSecurity.isPrivateHost(it)) }
    }

    @Test fun policy() {
        assertNull(EndpointSecurity.cleartextProblem("https://example.com/ingest"))
        assertNull(EndpointSecurity.cleartextProblem("http://127.0.0.1:8080/ingest"))
        assertNull(EndpointSecurity.cleartextProblem("http://hs.example.ts.net/api"))
        assertNotNull(EndpointSecurity.cleartextProblem("http://example.com/ingest"))
        assertNotNull(EndpointSecurity.cleartextProblem("ftp://example.com"))
        assertNotNull(EndpointSecurity.cleartextProblem("http://"))
    }
}
