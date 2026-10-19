package com.victorious.browser.liteapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.victorious.browser.R
import com.victorious.browser.panels.Panel

object LiteAppShortcuts {
    /** Asks the launcher to pin [panel] as a Lite App. Returns false if pinning is unsupported. */
    fun requestPin(context: Context, panel: Panel): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val launch = Intent(context, LiteAppActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(LiteAppLinks.dataString(panel.id)))
        val info = ShortcutInfoCompat.Builder(context, LiteAppLinks.shortcutId(panel.id))
            .setShortLabel(panel.name)
            .setLongLabel(panel.name)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(launch)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }
}
