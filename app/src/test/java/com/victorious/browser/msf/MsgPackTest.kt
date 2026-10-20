package com.victorious.browser.msf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MsgPackTest {
    private fun roundTrip(v: Any?): Any? = MsgPack.decode(MsgPack.encode(v))

    @Test fun nilBool() {
        assertNull(roundTrip(null))
        assertEquals(true, roundTrip(true))
        assertEquals(false, roundTrip(false))
    }

    @Test fun ints() {
        val cases = listOf(0L, 1L, 127L, 128L, 255L, 256L, 65535L, 65536L,
            4294967295L, 4294967296L, -1L, -32L, -33L, -128L, -129L,
            -32768L, -32769L, -2147483648L, -2147483649L, Long.MAX_VALUE)
        for (c in cases) assertEquals("int $c", c, roundTrip(c))
    }

    @Test fun intFromKotlinInt() {
        assertEquals(42L, roundTrip(42))
    }

    @Test fun doubles() {
        assertEquals(3.14, roundTrip(3.14) as Double, 1e-9)
        assertEquals(-0.5, roundTrip(-0.5) as Double, 1e-9)
    }

    @Test fun strings() {
        for (s in listOf("", "hi", "a".repeat(31), "b".repeat(32), "c".repeat(300),
            "unicode-\u00e9\u00fc", "x".repeat(70000))) {
            assertEquals(s, roundTrip(s))
        }
    }

    @Test fun binary() {
        val b = byteArrayOf(0, 1, 2, -1, 127, -128)
        assertArrayEquals(b, roundTrip(b) as ByteArray)
    }

    @Test fun arrays() {
        val a = listOf(1L, "two", true, null, listOf(3L, 4L))
        assertEquals(a, roundTrip(a))
    }

    @Test fun bigArray() {
        val a = (0 until 20).map { it.toLong() }
        assertEquals(a, roundTrip(a))
    }

    @Test fun maps() {
        val m = linkedMapOf<Any?, Any?>("result" to "success", "token" to "abc123", "n" to 5L)
        @Suppress("UNCHECKED_CAST")
        val out = roundTrip(m) as Map<Any?, Any?>
        assertEquals("success", out["result"])
        assertEquals("abc123", out["token"])
        assertEquals(5L, out["n"])
    }

    @Test fun nestedRpcLikeStructure() {
        val call = listOf("console.read", "tok", "1")
        @Suppress("UNCHECKED_CAST")
        val out = roundTrip(call) as List<Any?>
        assertEquals("console.read", out[0])
        assertEquals("tok", out[1])
        assertEquals("1", out[2])
    }
}
