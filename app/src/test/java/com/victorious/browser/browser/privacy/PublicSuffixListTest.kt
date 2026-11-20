package com.victorious.browser.browser.privacy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicSuffixListTest {
    /** The real bundled list (unit tests run with the module dir as cwd). */
    private val psl: PublicSuffixList by lazy {
        File("src/main/assets/psl/public_suffix_list.dat").inputStream().use { PublicSuffixList.parse(it) }
    }

    @Test fun bundledAssetIsTheOfficialList() {
        val head = File("src/main/assets/psl/public_suffix_list.dat").readLines().take(3).joinToString("\n")
        assertTrue(head.contains("mozilla.org/MPL/2.0"))
        assertTrue(File("src/main/assets/psl/public_suffix_list.dat").length() > 100_000)
    }

    @Test fun simpleAndMultiPartSuffixes() {
        assertEquals("example.com", psl.registrableDomain("www.example.com"))
        assertEquals("example.co.uk", psl.registrableDomain("a.b.example.co.uk"))
        assertEquals("example.com.au", psl.registrableDomain("x.example.com.au"))
        assertEquals("example.com", psl.registrableDomain("example.com"))
    }

    @Test fun privateSectionSuffixesSeparateSites() {
        assertEquals("alice.github.io", psl.registrableDomain("alice.github.io"))
        assertEquals("alice.github.io", psl.registrableDomain("www.alice.github.io"))
        assertEquals("bob.blogspot.com", psl.registrableDomain("x.bob.blogspot.com"))
    }

    @Test fun wildcardAndExceptionRules() {
        // *.ck is a wildcard rule, !www.ck an exception (publicsuffix.org test vectors).
        assertEquals("b.c.ck", psl.registrableDomain("a.b.c.ck"))
        assertEquals("www.ck", psl.registrableDomain("www.ck"))
        assertEquals("www.ck", psl.registrableDomain("sub.www.ck"))
        assertEquals("c.ck", psl.publicSuffix("a.b.c.ck"))
    }

    @Test fun hostThatIsASuffixIsReturnedAsIs() {
        assertTrue(psl.isPublicSuffix("co.uk"))
        assertTrue(psl.isPublicSuffix("com"))
        assertTrue(psl.isPublicSuffix("github.io"))
        assertFalse(psl.isPublicSuffix("example.com"))
        assertEquals("co.uk", psl.registrableDomain("co.uk"))
    }

    @Test fun unknownTldUsesImplicitStarRule() {
        assertEquals("example.invalidtld", psl.registrableDomain("www.example.invalidtld"))
    }

    @Test fun normalisation() {
        assertEquals("example.co.uk", psl.registrableDomain("WWW.Example.CO.UK."))
        // IDN host and its punycode form resolve identically.
        assertEquals(psl.registrableDomain("www.bücher.de"), psl.registrableDomain("www.xn--bcher-kva.de"))
        assertNull(psl.registrableDomain(""))
        assertNull(psl.registrableDomain("a..b"))
    }

    @Test fun hostUtilUsesInstalledList() {
        PublicSuffixes.install(psl)
        try {
            assertEquals("alice.github.io", HostUtil.baseDomain("x.alice.github.io"))
            assertEquals("example.co.uk", HostUtil.baseDomain("a.example.co.uk"))
            assertEquals("10.0.0.1", HostUtil.baseDomain("10.0.0.1"))
            assertTrue(HostUtil.isThirdParty("bob.github.io", "alice.github.io"))
            assertFalse(HostUtil.isThirdParty("cdn.example.com", "www.example.com"))
        } finally {
            PublicSuffixes.install(null)
        }
    }

    @Test fun fallbackKeepsOldBehaviourWhenAssetMissing() {
        PublicSuffixes.install(null)
        PublicSuffixes.opener = null
        assertEquals("example.co.uk", HostUtil.baseDomain("a.example.co.uk"))
        assertEquals("example.com", HostUtil.baseDomain("a.example.com"))
    }
}
