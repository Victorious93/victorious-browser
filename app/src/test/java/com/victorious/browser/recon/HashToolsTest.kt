package com.victorious.browser.recon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HashToolsTest {

    @Test
    fun knownVectors_abc() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", HashTools.hash("abc", HashTools.Algorithm.MD5))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", HashTools.hash("abc", HashTools.Algorithm.SHA1))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            HashTools.hash("abc", HashTools.Algorithm.SHA256),
        )
        assertEquals(
            "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a" +
                "2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
            HashTools.hash("abc", HashTools.Algorithm.SHA512),
        )
    }

    @Test
    fun emptyString_md5Vector() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", HashTools.hash("", HashTools.Algorithm.MD5))
    }

    @Test
    fun hashAll_returnsAllFour_withCorrectLengths() {
        val all = HashTools.hashAll("victorious")
        assertEquals(4, all.size)
        all.forEach { (algo, hex) -> assertEquals(algo.hexLen, hex.length) }
    }

    @Test
    fun identify_md5LengthGuess() {
        val id = HashTools.identify("900150983cd24fb0d6963f7d28e17f72")
        assertTrue(id.isHex)
        assertEquals(32, id.length)
        assertTrue(id.guesses.any { it.label == "MD5" })
        assertTrue(id.guesses.any { it.label == "NTLM" })
    }

    @Test
    fun identify_sha1AndSha256AndSha512() {
        assertTrue(HashTools.identify("a".repeat(40)).guesses.any { it.label == "SHA-1" })
        assertTrue(HashTools.identify("a".repeat(64)).guesses.any { it.label == "SHA-256" })
        assertTrue(HashTools.identify("a".repeat(128)).guesses.any { it.label == "SHA-512" })
    }

    @Test
    fun identify_bcryptPrefix() {
        val id = HashTools.identify("\$2b\$12\$abcdefghijklmnopqrstuv")
        assertFalse(id.isHex)
        assertTrue(id.guesses.any { it.label == "bcrypt" })
    }

    @Test
    fun identify_cryptSha512() {
        val id = HashTools.identify("\$6\$salt\$hashvalue")
        assertTrue(id.guesses.any { it.label == "sha512crypt" })
    }

    @Test
    fun identify_emptyAndUnknown() {
        assertTrue(HashTools.identify("").guesses.any { it.label == "empty" })
        assertTrue(HashTools.identify("xyz!not-hex").guesses.any { it.label == "unknown" })
        assertTrue(HashTools.identify("abcdef").guesses.any { it.label.contains("unknown hex") })
    }

    @Test
    fun dictionaryCheck_findsMatch_autoNarrowsByLength() {
        val target = HashTools.hash("secret", HashTools.Algorithm.SHA256)
        val result = HashTools.dictionaryCheck(
            target,
            sequenceOf("foo", "bar", "secret", "baz"),
        )
        assertTrue(result.matched)
        assertEquals("secret", result.plaintext)
        assertEquals(HashTools.Algorithm.SHA256, result.algo)
        assertEquals(3, result.candidatesTried)
    }

    @Test
    fun dictionaryCheck_noMatch() {
        val target = HashTools.hash("secret", HashTools.Algorithm.MD5)
        val result = HashTools.dictionaryCheck(target, sequenceOf("a", "b", "c"))
        assertFalse(result.matched)
        assertNull(result.plaintext)
        assertEquals(3, result.candidatesTried)
    }

    @Test
    fun dictionaryCheck_explicitAlgo() {
        val target = HashTools.hash("pw", HashTools.Algorithm.SHA1)
        val result = HashTools.dictionaryCheck(
            target,
            sequenceOf("pw"),
            algos = listOf(HashTools.Algorithm.SHA1),
        )
        assertTrue(result.matched)
        assertEquals(HashTools.Algorithm.SHA1, result.algo)
    }

    @Test
    fun dictionaryCheck_noMatchingAlgoLength_triesNothing() {
        val result = HashTools.dictionaryCheck("abc", sequenceOf("x", "y"))
        assertFalse(result.matched)
        assertEquals(0, result.candidatesTried)
    }
}
