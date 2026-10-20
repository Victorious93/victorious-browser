package com.victorious.browser.certmgr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaGeneratorTest {

    @Test fun generatesSelfSignedCaWithKeyStore() {
        val material = CaGenerator.generateRoot(validityDays = 30)
        val cert = material.certificate
        // self-signed: issuer == subject
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        // CA: basic constraints pathLen present (>=0 or Int.MAX for no limit)
        assertTrue("should be a CA cert", cert.basicConstraints >= 0)
        // cert verifies with its own public key
        cert.verify(material.keyPair.public)

        val ks = CaGenerator.toKeyStore(material, "inspect".toCharArray())
        assertNotNull(ks.getCertificate("root"))
        assertNotNull(ks.getKey("root", "inspect".toCharArray()))
        // DER encodes and fingerprints
        val fp = CertFormatter.sha256Fingerprint(cert.encoded)
        assertEquals(32, fp.split(":").size)
    }
}
