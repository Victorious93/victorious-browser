package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure tab-store logic: open / close / activate / ordering and
 * the active-tab invariants. No Android runtime.
 *
 * A deterministic id generator (t0, t1, …) is injected so assertions are stable.
 */
class TabStoreTest {

    private fun store(): TabStore {
        var n = 0
        return TabStore(idGenerator = { "t${n++}" })
    }

    @Test
    fun starts_empty_with_no_active_tab() {
        val s = store()
        assertEquals(0, s.count)
        assertNull(s.activeTabId.value)
        assertNull(s.activeTab())
    }

    @Test
    fun open_appends_and_activates_by_default() {
        val s = store()
        val a = s.openTab("https://a.example")
        assertEquals("t0", a)
        assertEquals(1, s.count)
        assertEquals(a, s.activeTabId.value)

        val b = s.openTab("https://b.example")
        assertEquals(listOf("t0", "t1"), s.tabs.value.map { it.id })
        assertEquals(b, s.activeTabId.value)
    }

    @Test
    fun open_in_background_keeps_active_but_first_tab_activates() {
        val s = store()
        val a = s.openTab("https://a.example", activate = false)
        // First-ever tab activates even when activate=false (invariant: non-empty ⇒ active set).
        assertEquals(a, s.activeTabId.value)

        val b = s.openTab("https://b.example", activate = false)
        assertEquals(a, s.activeTabId.value)
        assertTrue(s.tabs.value.any { it.id == b })
    }

    @Test
    fun ordering_is_insertion_order() {
        val s = store()
        s.openTab("u0"); s.openTab("u1"); s.openTab("u2")
        assertEquals(listOf("t0", "t1", "t2"), s.tabs.value.map { it.id })
    }

    @Test
    fun activate_switches_active_tab() {
        val s = store()
        val a = s.openTab("u0"); s.openTab("u1")
        s.activate(a)
        assertEquals(a, s.activeTabId.value)
    }

    @Test
    fun activate_unknown_id_is_ignored() {
        val s = store()
        val a = s.openTab("u0")
        s.activate("does-not-exist")
        assertEquals(a, s.activeTabId.value)
    }

    @Test
    fun close_nonactive_leaves_active_unchanged() {
        val s = store()
        val a = s.openTab("u0")
        val b = s.openTab("u1") // b active
        s.closeTab(a)
        assertEquals(listOf("t1"), s.tabs.value.map { it.id })
        assertEquals(b, s.activeTabId.value)
    }

    @Test
    fun close_active_middle_selects_next_tab() {
        val s = store()
        s.openTab("u0")                 // t0
        val b = s.openTab("u1")         // t1
        s.openTab("u2")                 // t2
        s.activate(b)                   // active = t1 (index 1)
        s.closeTab(b)
        assertEquals(listOf("t0", "t2"), s.tabs.value.map { it.id })
        assertEquals("t2", s.activeTabId.value)
    }

    @Test
    fun close_active_last_selects_previous_tab() {
        val s = store()
        s.openTab("u0")                 // t0
        s.openTab("u1")                 // t1
        val c = s.openTab("u2")         // t2 active (last)
        s.closeTab(c)
        assertEquals(listOf("t0", "t1"), s.tabs.value.map { it.id })
        assertEquals("t1", s.activeTabId.value)
    }

    @Test
    fun closing_last_remaining_tab_clears_active() {
        val s = store()
        val a = s.openTab("u0")
        s.closeTab(a)
        assertEquals(0, s.count)
        assertNull(s.activeTabId.value)
        assertNull(s.activeTab())
    }

    @Test
    fun close_unknown_id_is_noop() {
        val s = store()
        val a = s.openTab("u0")
        s.closeTab("nope")
        assertEquals(1, s.count)
        assertEquals(a, s.activeTabId.value)
    }

    @Test
    fun active_tab_id_always_references_existing_tab_after_mutations() {
        val s = store()
        val ids = (0 until 5).map { s.openTab("u$it") }
        for (id in listOf(ids[2], ids[0], ids[4], ids[1], ids[3])) {
            s.closeTab(id)
            val active = s.activeTabId.value
            if (s.count == 0) {
                assertNull(active)
            } else {
                assertTrue(s.tabs.value.any { it.id == active })
            }
        }
        assertEquals(0, s.count)
    }

    @Test
    fun updates_mutate_the_right_tab_only() {
        val s = store()
        val a = s.openTab("https://a.example")
        val b = s.openTab("https://b.example")
        s.updateTitle(a, "Alpha")
        s.updateUrl(b, "https://b2.example")
        s.updateNav(a, canGoBack = true, canGoForward = false)

        val ta = s.get(a)!!
        val tb = s.get(b)!!
        assertEquals("Alpha", ta.title)
        assertTrue(ta.canGoBack)
        assertFalse(ta.canGoForward)
        assertEquals("https://b2.example", tb.url)
        assertEquals("https://a.example", ta.url)
    }

    @Test
    fun indexOf_and_get_reflect_state() {
        val s = store()
        val a = s.openTab("u0")
        val b = s.openTab("u1")
        assertEquals(0, s.indexOf(a))
        assertEquals(1, s.indexOf(b))
        assertEquals(-1, s.indexOf("missing"))
        assertNull(s.get("missing"))
    }

    @Test
    fun closeAll_empties_store() {
        val s = store()
        s.openTab("u0"); s.openTab("u1")
        s.closeAll()
        assertEquals(0, s.count)
        assertNull(s.activeTabId.value)
    }
}
