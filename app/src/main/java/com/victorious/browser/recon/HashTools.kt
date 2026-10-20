package com.victorious.browser.recon

import java.security.MessageDigest
import java.util.Locale

/**
 * Pure, Android-free hashing + hash-identification helpers for the Recon Workbench.
 *
 * - Compute MD5 / SHA-1 / SHA-256 / SHA-512 of input text (defensive: hashing is
 *   one-way and operates only on text the operator already holds).
 * - Identify a likely hash type from a bare string by length + charset. This is a
 *   HEURISTIC GUESS, clearly labeled as such — many distinct algorithms share a
 *   digest length, so a length match is never proof.
 * - An optional dictionary check: hash each line of a user-supplied wordlist and
 *   compare to a target digest. No wordlist is bundled here; the operator provides
 *   the candidate lines.
 */
object HashTools {

    enum class Algorithm(val label: String, val jdkName: String, val hexLen: Int) {
        MD5("MD5", "MD5", 32),
        SHA1("SHA-1", "SHA-1", 40),
        SHA256("SHA-256", "SHA-256", 64),
        SHA512("SHA-512", "SHA-512", 128),
    }

    /** Lowercase hex digest of [input] (UTF-8) under [algo]. */
    fun hash(input: String, algo: Algorithm): String {
        val md = MessageDigest.getInstance(algo.jdkName)
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return toHex(bytes)
    }

    /** Convenience: compute all four digests at once. */
    fun hashAll(input: String): Map<Algorithm, String> =
        Algorithm.entries.associateWith { hash(input, it) }

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b.toInt() and 0xFF))
        return sb.toString()
    }

    // --- Identification ---------------------------------------------------------

    data class Guess(
        val label: String,
        /** Human note on why this matched / caveats. */
        val note: String,
    )

    data class Identification(
        val normalized: String,
        val isHex: Boolean,
        val length: Int,
        /** Ordered best-guess candidates. Always treat as a guess, never proof. */
        val guesses: List<Guess>,
    )

    /**
     * Heuristically identify a hash string by length and character set.
     * Returns an [Identification] whose guesses are explicitly advisory.
     */
    fun identify(raw: String): Identification {
        val s = raw.trim()
        val isHex = s.isNotEmpty() && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
        val guesses = mutableListOf<Guess>()
        if (s.isEmpty()) {
            return Identification(s, false, 0, listOf(Guess("empty", "No input to identify")))
        }
        if (isHex) {
            when (s.length) {
                32 -> {
                    guesses += Guess("MD5", "32 hex chars — also NTLM, MD4, MD2, LM half, HAVAL-128")
                    guesses += Guess("NTLM", "32 hex chars — common Windows hash, indistinguishable from MD5 by length")
                }
                40 -> {
                    guesses += Guess("SHA-1", "40 hex chars — also RIPEMD-160, HAVAL-160")
                }
                56 -> guesses += Guess("SHA-224", "56 hex chars — also SHA3-224")
                64 -> {
                    guesses += Guess("SHA-256", "64 hex chars — also SHA3-256, RIPEMD-256, BLAKE2s-256")
                }
                96 -> guesses += Guess("SHA-384", "96 hex chars — also SHA3-384")
                128 -> {
                    guesses += Guess("SHA-512", "128 hex chars — also SHA3-512, BLAKE2b-512, Whirlpool")
                }
                16 -> guesses += Guess("CRC-64 / MySQL<4.1", "16 hex chars — short digest or checksum")
                8 -> guesses += Guess("CRC-32 / Adler-32", "8 hex chars — checksum, not a cryptographic hash")
                else -> guesses += Guess("unknown hex", "${s.length} hex chars — no common algorithm has this length")
            }
        } else {
            // Non-hex prefixed/encoded formats.
            when {
                s.startsWith("\$2a\$") || s.startsWith("\$2b\$") || s.startsWith("\$2y\$") ->
                    guesses += Guess("bcrypt", "\$2x\$ prefix — Blowfish-based password hash")
                s.startsWith("\$argon2") ->
                    guesses += Guess("Argon2", "\$argon2 prefix — modern password hash")
                s.startsWith("\$6\$") -> guesses += Guess("sha512crypt", "\$6\$ crypt(3) format")
                s.startsWith("\$5\$") -> guesses += Guess("sha256crypt", "\$5\$ crypt(3) format")
                s.startsWith("\$1\$") -> guesses += Guess("md5crypt", "\$1\$ crypt(3) format")
                s.startsWith("\$y\$") || s.startsWith("\$7\$") ->
                    guesses += Guess("yescrypt/scrypt", "crypt(3) KDF format")
                Regex("^[A-Za-z0-9+/]+={0,2}$").matches(s) && s.length % 4 == 0 ->
                    guesses += Guess("base64-wrapped digest", "Looks base64 — decode first, then identify the raw bytes")
                else -> guesses += Guess("unknown", "Not hex and no recognized prefix")
            }
        }
        return Identification(s, isHex, s.length, guesses)
    }

    // --- Dictionary check (pure logic; caller supplies candidate lines) ---------

    data class CrackResult(
        val matched: Boolean,
        val plaintext: String? = null,
        val algo: Algorithm? = null,
        val candidatesTried: Int = 0,
    )

    /**
     * Try each candidate line against [targetHex]. If [algos] is empty, pick the
     * algorithms whose digest length matches the target (narrowing the search).
     * The caller provides the wordlist; nothing is bundled.
     */
    fun dictionaryCheck(
        targetHex: String,
        candidates: Sequence<String>,
        algos: Collection<Algorithm> = emptyList(),
    ): CrackResult {
        val target = targetHex.trim().lowercase(Locale.US)
        val useAlgos = when {
            algos.isNotEmpty() -> algos.toList()
            else -> Algorithm.entries.filter { it.hexLen == target.length }
        }
        if (useAlgos.isEmpty()) {
            return CrackResult(matched = false, candidatesTried = 0)
        }
        var tried = 0
        for (rawLine in candidates) {
            val line = rawLine.trimEnd('\n', '\r')
            tried++
            for (a in useAlgos) {
                if (hash(line, a) == target) {
                    return CrackResult(true, line, a, tried)
                }
            }
        }
        return CrackResult(false, candidatesTried = tried)
    }
}
