package tj.behruz.devicesignals.sdk.internal.modules

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import tj.behruz.devicesignals.sdk.Module
import tj.behruz.devicesignals.sdk.internal.CollectContext
import tj.behruz.devicesignals.sdk.internal.DeviceHasher
import tj.behruz.devicesignals.sdk.internal.ModuleResult
import tj.behruz.devicesignals.sdk.internal.SignalModule
import java.net.NetworkInterface

internal class NetworkModule : SignalModule {
    override val module = Module.NETWORK

    override suspend fun collect(context: CollectContext): ModuleResult {
        val appContext = context.appContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val missingFields = mutableListOf<String>()

        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }

        val connectionType = when {
            caps == null -> "NONE"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "UNKNOWN"
        }

        val isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

        val vpnInterfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.name.startsWith("tun") || it.name.startsWith("pptp") || it.name.startsWith("ppp") }
                ?.map { it.name }
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // WiFi info - requires ACCESS_FINE_LOCATION + ACCESS_WIFI_STATE
        var wifiSsidHash: String? = null
        var wifiBssidHash: String? = null
        var wifiFrequencyMhz: Int? = null

        val hasLocationPerm = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPerm && connectionType == "WIFI") {
            try {
                val wm = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                @Suppress("DEPRECATION")
                val wifiInfo = wm.connectionInfo
                if (wifiInfo != null) {
                    val ssid = wifiInfo.ssid?.removeSurrounding("\"")
                    if (ssid != null && ssid != "<unknown ssid>") {
                        wifiSsidHash = DeviceHasher.sha256(ssid)
                    }
                    val bssid = wifiInfo.bssid
                    if (bssid != null && bssid != "02:00:00:00:00:00") {
                        wifiBssidHash = DeviceHasher.sha256(bssid)
                    }
                    wifiFrequencyMhz = wifiInfo.frequency
                }
            } catch (_: Exception) { }
        } else if (connectionType == "WIFI") {
            missingFields.addAll(listOf("wifi_ssid_hash", "wifi_bssid_hash", "wifi_frequency_mhz"))
        }

        // Proxy
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPortStr = System.getProperty("http.proxyPort")
        val proxyPort = proxyPortStr?.toIntOrNull()
        val isProxyConfigured = !proxyHost.isNullOrBlank()

        val data = mapOf(
            "connection_type" to connectionType,
            "is_vpn_active" to isVpnActive,
            "vpn_interfaces" to vpnInterfaces,
            "wifi_ssid_hash" to wifiSsidHash,
            "wifi_bssid_hash" to wifiBssidHash,
            "wifi_frequency_mhz" to wifiFrequencyMhz,
            "proxy_host" to proxyHost,
            "proxy_port" to proxyPort,
            "is_proxy_configured" to isProxyConfigured,
        )
        return ModuleResult(data = data, missingFields = missingFields)
    }

}
