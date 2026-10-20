package com.victorious.browser.netid

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build

/**
 * Read-only information about Android's per-network randomized MAC.
 *
 * A normal (non-root, non-system) Android app CANNOT change the device Wi-Fi MAC
 * address. This surfaces what the platform will tell us and documents the limit.
 */
object MacStatus {
    data class Info(
        val wifiConnected: Boolean,
        val ssid: String?,
        /** Platform-reported randomized-MAC status for the active Wi-Fi, when available. */
        val randomizedMacStatus: String,
        val limitationNote: String = LIMITATION,
    )

    const val LIMITATION =
        "A normal (non-root, non-system) Android app cannot change the device Wi-Fi MAC " +
            "address. Android randomizes MACs per network at the OS / Settings level " +
            "(Privacy → Wi-Fi → MAC address type). Victorious Browser only reports status; " +
            "it does not spoof a hardware MAC."

    fun read(context: Context): Info {
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val network = cm?.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        var ssid: String? = null
        var status = "Unavailable (no Wi-Fi connection or API denied)"

        if (wifi != null && wifiConnected) {
            try {
                @Suppress("DEPRECATION")
                val info = wifi.connectionInfo
                ssid = info?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                status = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        // WifiInfo.getMacAddress() returns a randomized/placeholder value on
                        // Android 10+ for privacy; WifiConfiguration.isRandomizedMacAddress /
                        // WifiNetworkSpecifier APIs that expose randomized-MAC preference are
                        // Settings-app / privileged. Surface an honest summary.
                        "Android ${Build.VERSION.RELEASE}: randomized MAC is managed by the " +
                            "system (Settings → Network & internet → Wi-Fi → [network] → Privacy). " +
                            "Apps cannot read or set the real hardware MAC without privileged APIs."
                    }
                    else -> {
                        "Pre-Android 10: randomized MAC not enforced platform-wide. " +
                            "This app still cannot change the MAC without root / system privileges."
                    }
                }
            } catch (_: SecurityException) {
                status = "Permission denied reading Wi-Fi info (ACCESS_WIFI_STATE / location)."
            } catch (_: Exception) {
                status = "Could not read Wi-Fi connection info."
            }
        }

        return Info(wifiConnected = wifiConnected, ssid = ssid, randomizedMacStatus = status)
    }
}
