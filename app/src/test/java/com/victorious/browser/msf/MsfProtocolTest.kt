package com.victorious.browser.msf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MsfProtocolTest {
    @Test fun authLoginHasNoToken() {
        val call = MsfProtocol.buildCall("auth.login", "ignored", listOf("user", "pass"))
        assertEquals(listOf("auth.login", "user", "pass"), call)
    }

    @Test fun authenticatedCallInsertsToken() {
        val call = MsfProtocol.buildCall("console.read", "TOK", listOf("1"))
        assertEquals(listOf("console.read", "TOK", "1"), call)
    }

    @Test fun nullTokenOmitted() {
        val call = MsfProtocol.buildCall("core.version", null, emptyList())
        assertEquals(listOf("core.version"), call)
    }

    @Test fun encodeCallRoundTrips() {
        val bytes = MsfProtocol.encodeCall("console.write", "TOK", listOf("1", "help\n"))
        @Suppress("UNCHECKED_CAST")
        val decoded = MsgPack.decode(bytes) as List<Any?>
        assertEquals(listOf("console.write", "TOK", "1", "help\n"), decoded)
    }

    @Test fun asStringCoercesBytes() {
        assertEquals("abc", MsfProtocol.asString("abc"))
        assertEquals("abc", MsfProtocol.asString("abc".toByteArray()))
        assertNull(MsfProtocol.asString(null))
    }

    @Test fun mapGetHandlesByteArrayKeys() {
        val map = linkedMapOf<Any?, Any?>("token".toByteArray() to "T", "result" to "ok")
        assertEquals("T", MsfProtocol.asString(MsfProtocol.mapGet(map, "token")))
        assertEquals("ok", MsfProtocol.mapGet(map, "result"))
    }

    @Test fun errorDetection() {
        val err = linkedMapOf<Any?, Any?>("error" to true, "error_message" to "bad")
        assertTrue(MsfProtocol.isError(err))
        assertEquals("bad", MsfProtocol.errorMessage(err))
        val ok = linkedMapOf<Any?, Any?>("result" to "success")
        assertFalse(MsfProtocol.isError(ok))
    }

    @Test fun extractToken() {
        val resp = linkedMapOf<Any?, Any?>("result" to "success", "token" to "deadbeef")
        assertEquals("deadbeef", MsfProtocol.extractToken(resp))
        assertEquals("success", MsfProtocol.resultString(resp))
    }
}
