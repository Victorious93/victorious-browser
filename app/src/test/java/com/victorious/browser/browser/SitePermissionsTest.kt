package com.victorious.browser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SitePermissionsTest {

    @Test
    fun hostKeyNormalises() {
        assertEquals("example.com", SitePermissionLogic.hostKey("https://Example.COM/path?q=1"))
        assertNull(SitePermissionLogic.hostKey("about:blank"))
        assertNull(SitePermissionLogic.hostKey(null))
        assertNull(SitePermissionLogic.hostKey(""))
    }

    @Test
    fun resourcesMapToPermissions() {
        val both = SitePermissionLogic.permissionsForResources(
            arrayOf(SitePermissionLogic.RESOURCE_VIDEO, SitePermissionLogic.RESOURCE_AUDIO, "other"),
        )
        assertEquals(setOf(SitePermission.CAMERA, SitePermission.MIC), both)
        assertTrue(SitePermissionLogic.permissionsForResources(null).isEmpty())
    }

    @Test
    fun grantedResourcesHonoursDecisions() {
        val resources = arrayOf(SitePermissionLogic.RESOURCE_VIDEO, SitePermissionLogic.RESOURCE_AUDIO)
        val granted = SitePermissionLogic.grantedResources(resources) {
            if (it == SitePermission.CAMERA) PermissionDecision.ALLOW else PermissionDecision.BLOCK
        }
        assertEquals(listOf(SitePermissionLogic.RESOURCE_VIDEO), granted.toList())
    }

    @Test
    fun jsDefaultsAllowWhenAsk() {
        assertEquals(PermissionDecision.ALLOW, SitePermissionLogic.effective(SitePermission.JAVASCRIPT, PermissionDecision.ASK))
        assertEquals(PermissionDecision.ASK, SitePermissionLogic.effective(SitePermission.CAMERA, PermissionDecision.ASK))
        assertEquals(PermissionDecision.BLOCK, SitePermissionLogic.effective(SitePermission.CAMERA, PermissionDecision.BLOCK))
    }

    @Test
    fun encodeDecodeRoundTrip() {
        val map = mapOf(
            "example.com" to mapOf(
                SitePermission.CAMERA to PermissionDecision.ALLOW,
                SitePermission.POPUPS to PermissionDecision.BLOCK,
            ),
            "other.test" to mapOf(SitePermission.MIC to PermissionDecision.ALLOW),
        )
        val round = SitePermissionLogic.decode(SitePermissionLogic.encode(map))
        assertEquals(map, round)
    }

    @Test
    fun askRowsAreNotPersisted() {
        val withAsk = mapOf("a.com" to mapOf(SitePermission.CAMERA to PermissionDecision.ASK))
        assertEquals("{}", SitePermissionLogic.encode(withAsk))
        assertTrue(SitePermissionLogic.decode("{}").isEmpty())
        assertTrue(SitePermissionLogic.decode(null).isEmpty())
        assertTrue(SitePermissionLogic.decode("not-json").isEmpty())
    }

    @Test
    fun withDecisionAndClear() {
        var map = emptyMap<String, Map<SitePermission, PermissionDecision>>()
        map = SitePermissionLogic.withDecision(map, "a.com", SitePermission.CAMERA, PermissionDecision.ALLOW)
        map = SitePermissionLogic.withDecision(map, "a.com", SitePermission.MIC, PermissionDecision.BLOCK)
        assertEquals(PermissionDecision.ALLOW, map["a.com"]!![SitePermission.CAMERA])
        map = SitePermissionLogic.withDecision(map, "a.com", SitePermission.CAMERA, PermissionDecision.ASK)
        assertNull(map["a.com"]!![SitePermission.CAMERA])
        map = SitePermissionLogic.cleared(map, "a.com")
        assertTrue(map.isEmpty())
    }
}
