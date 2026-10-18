package com.victorious.browser.inspect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayPrepareTest {

    @Test
    fun prepareForWire_addsReplayMarker_uppercasesMethod_syncsLength() {
        val edited = EditableRequest(
            method = "post",
            url = "http://127.0.0.1:8080/api",
            headers = listOf(CapturedHeader("Content-Type", "text/plain")),
            body = "ping".toByteArray(),
        )
        val wire = ReplayClient.prepareForWire(edited)
        assertEquals("POST", wire.method)
        assertTrue(
            wire.headers.any {
                it.name.equals(InterceptController.REPLAY_MARKER_HEADER, true) && it.value == "1"
            },
        )
        assertEquals("4", wire.headers.first { it.name.equals("Content-Length", true) }.value)
        assertEquals("127.0.0.1:8080", wire.headers.first { it.name.equals("Host", true) }.value)
        assertEquals(
            1,
            wire.headers.count { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, true) },
        )
    }

    @Test
    fun prepareForWire_doesNotDuplicateMarker() {
        val edited = EditableRequest(
            method = "GET",
            url = "http://127.0.0.1/",
            headers = listOf(
                CapturedHeader(InterceptController.REPLAY_MARKER_HEADER, "old"),
                CapturedHeader("Accept", "*/*"),
            ),
        )
        val wire = ReplayClient.prepareForWire(edited)
        assertEquals(
            1,
            wire.headers.count { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, true) },
        )
        assertEquals(
            "1",
            wire.headers.first { it.name.equals(InterceptController.REPLAY_MARKER_HEADER, true) }.value,
        )
    }
}
