package com.victorious.browser.inspect

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InterceptControllerTest {

    @Before
    fun setUp() {
        InterceptController.resetForTests()
    }

    @After
    fun tearDown() {
        InterceptController.resetForTests()
    }

    @Test
    fun disabled_autoForwards() {
        val result = InterceptController.awaitDecision(
            1,
            EditableRequest("GET", "http://127.0.0.1/", emptyList()),
            timeoutMs = 500,
        )
        assertEquals(InterceptController.Decision.FORWARD, result.decision)
        assertNull(result.edited)
    }

    @Test
    fun enabled_forwardWithEdits() {
        InterceptController.setEnabled(true)
        val original = EditableRequest("GET", "http://127.0.0.1/a", emptyList())
        val edited = original.copy(method = "POST", url = "http://127.0.0.1/b")
        val exec = Executors.newSingleThreadExecutor()
        val future = exec.submit<InterceptController.InterceptResult> {
            InterceptController.awaitDecision(42, original, timeoutMs = 5_000)
        }
        var spins = 0
        while (InterceptController.pending.value == null && spins++ < 100) {
            Thread.sleep(20)
        }
        assertEquals(42L, InterceptController.pending.value?.exchangeId)
        InterceptController.forward(edited)
        val result = future.get(3, TimeUnit.SECONDS)
        assertEquals(InterceptController.Decision.FORWARD, result.decision)
        assertEquals("POST", result.edited?.method)
        assertEquals("http://127.0.0.1/b", result.edited?.url)
        exec.shutdownNow()
    }

    @Test
    fun enabled_drop() {
        InterceptController.setEnabled(true)
        val exec = Executors.newSingleThreadExecutor()
        val future = exec.submit<InterceptController.InterceptResult> {
            InterceptController.awaitDecision(
                9,
                EditableRequest("GET", "http://127.0.0.1/", emptyList()),
                timeoutMs = 5_000,
            )
        }
        var spins = 0
        while (InterceptController.pending.value == null && spins++ < 100) {
            Thread.sleep(20)
        }
        InterceptController.drop()
        val result = future.get(3, TimeUnit.SECONDS)
        assertEquals(InterceptController.Decision.DROP, result.decision)
        exec.shutdownNow()
    }

    @Test
    fun connectAndReplayMarker_neverPark() {
        InterceptController.setEnabled(true)
        val connect = InterceptController.awaitDecision(
            1,
            EditableRequest("CONNECT", "example.com:443", emptyList()),
            timeoutMs = 200,
        )
        assertEquals(InterceptController.Decision.FORWARD, connect.decision)

        val replay = InterceptController.awaitDecision(
            2,
            EditableRequest(
                "GET",
                "http://127.0.0.1/",
                listOf(CapturedHeader(InterceptController.REPLAY_MARKER_HEADER, "1")),
            ),
            timeoutMs = 200,
        )
        assertEquals(InterceptController.Decision.FORWARD, replay.decision)
        assertTrue(InterceptController.pending.value == null)
    }
}
