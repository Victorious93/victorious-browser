package com.victorious.browser

import android.app.Application
import com.victorious.browser.browser.privacy.PublicSuffixes
import com.victorious.browser.browser.privacy.TrackerListStore

class VictoriousApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Bundled Public Suffix List; parsed lazily on first lookup, not here.
        PublicSuffixes.opener = { assets.open("psl/public_suffix_list.dat") }
        // Re-apply a previously downloaded tracker-list update, if any.
        TrackerListStore.get(this).loadSaved()
    }
}
