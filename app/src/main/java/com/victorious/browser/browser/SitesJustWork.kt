package com.victorious.browser.browser

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Android helpers for Round 1 "sites just work" — runtime permission mapping,
 * immersive fullscreen enter/exit, and the decision tree that turns a page's
 * capability request into either an immediate grant/deny or a user prompt.
 * Kept out of TabbedBrowserScreen so the Compose host stays readable.
 */
object SitesJustWork {

    /** Android runtime permissions required to honour each [SitePermission]. */
    fun androidPermissionsFor(perms: Collection<SitePermission>): Array<String> {
        val out = linkedSetOf<String>()
        for (p in perms) when (p) {
            SitePermission.CAMERA -> out += Manifest.permission.CAMERA
            SitePermission.MIC -> out += Manifest.permission.RECORD_AUDIO
            SitePermission.LOCATION -> {
                out += Manifest.permission.ACCESS_FINE_LOCATION
                out += Manifest.permission.ACCESS_COARSE_LOCATION
            }
            SitePermission.POPUPS, SitePermission.JAVASCRIPT -> Unit
        }
        return out.toTypedArray()
    }

    /** True when every Android permission in [needed] is already granted. */
    fun hasAll(activity: Activity, needed: Array<String>): Boolean =
        needed.all { ContextCompat.checkSelfPermission(activity, it) == PackageManager.PERMISSION_GRANTED }

    /**
     * Resolve a getUserMedia / PermissionRequest against the site store (or a
     * private-tab transient map). Returns null when the user must be prompted;
     * otherwise the resources to grant (empty → deny).
     */
    fun resolvePermissionRequest(
        host: String?,
        resources: Array<String>?,
        isPrivate: Boolean,
        persisted: (SitePermission) -> PermissionDecision,
        transient: (SitePermission) -> PermissionDecision,
    ): Array<String>? {
        val asked = SitePermissionLogic.permissionsForResources(resources)
        if (asked.isEmpty()) return emptyArray() // nothing we know how to grant
        val decisions = asked.associateWith { p ->
            val stored = if (isPrivate) transient(p) else persisted(p)
            SitePermissionLogic.effective(p, stored)
        }
        // Any ASK → the UI must prompt; Always / Block decisions alone can finish now.
        if (decisions.values.any { it == PermissionDecision.ASK }) return null
        return SitePermissionLogic.grantedResources(resources) { decisions[it] ?: PermissionDecision.BLOCK }
    }

    /** Same idea for geolocation: null → prompt; true → allow; false → deny. */
    fun resolveGeo(
        host: String?,
        isPrivate: Boolean,
        persisted: (SitePermission) -> PermissionDecision,
        transient: (SitePermission) -> PermissionDecision,
    ): Boolean? {
        val stored = if (isPrivate) transient(SitePermission.LOCATION) else persisted(SitePermission.LOCATION)
        return when (SitePermissionLogic.effective(SitePermission.LOCATION, stored)) {
            PermissionDecision.ALLOW -> true
            PermissionDecision.BLOCK -> false
            PermissionDecision.ASK -> null
        }
    }

    fun enterImmersive(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun exitImmersive(activity: Activity) {
        WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * Apply a pending [PermissionRequest] after the user picked [choice]. Grants the
     * Android runtime permissions (caller must have obtained them) then grants /
     * denies the WebView resources. Returns the decision that should be persisted
     * (null for "allow once" / private).
     */
    fun applyPermissionChoice(
        request: PermissionRequest,
        choice: PromptChoice,
        isPrivate: Boolean,
    ): PermissionDecision? {
        val decision = when (choice) {
            PromptChoice.ALLOW_ONCE, PromptChoice.ALWAYS -> PermissionDecision.ALLOW
            PromptChoice.BLOCK -> PermissionDecision.BLOCK
        }
        if (decision == PermissionDecision.ALLOW) {
            request.grant(SitePermissionLogic.grantedResources(request.resources) { PermissionDecision.ALLOW })
        } else {
            request.deny()
        }
        // Persist only Always/Block on non-private tabs.
        return when {
            isPrivate -> null
            choice == PromptChoice.ALWAYS -> PermissionDecision.ALLOW
            choice == PromptChoice.BLOCK -> PermissionDecision.BLOCK
            else -> null // allow once
        }
    }

    fun applyGeoChoice(
        origin: String?,
        callback: GeolocationPermissions.Callback,
        choice: PromptChoice,
        isPrivate: Boolean,
    ): PermissionDecision? {
        val allow = choice == PromptChoice.ALLOW_ONCE || choice == PromptChoice.ALWAYS
        // remember = false always — we own persistence, not the WebView geolocation store.
        callback.invoke(origin, allow, false)
        return when {
            isPrivate -> null
            choice == PromptChoice.ALWAYS -> PermissionDecision.ALLOW
            choice == PromptChoice.BLOCK -> PermissionDecision.BLOCK
            else -> null
        }
    }

    /** Orientation hint from a custom-view (fullscreen video). Returns null when unknown. */
    fun orientationFrom(view: View?): Int? {
        // WebView does not expose a reliable "video is landscape" flag; leave
        // orientation alone and let the system / user rotate. Returning null tells
        // the host not to lock.
        return null
    }

    /** PiP is only useful on API 26+ and when the activity declared supportsPictureInPicture. */
    fun canEnterPip(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }
}

/** Pending getUserMedia / geolocation prompt awaiting the user's choice. */
sealed class PendingSitePrompt {
    data class Media(
        val host: String,
        val permissions: List<SitePermission>,
        val request: PermissionRequest,
        val isPrivate: Boolean,
    ) : PendingSitePrompt()

    data class Geo(
        val host: String,
        val origin: String?,
        val callback: GeolocationPermissions.Callback,
        val isPrivate: Boolean,
    ) : PendingSitePrompt()
}
