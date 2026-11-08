package com.custom.vox.homemonitor.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

/**
 * Lightweight local network readout — no cards, no external API.
 */
class NetworkStatusReader(private val context: Context) {

    fun read(): NetworkSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val props = network?.let { cm.getLinkProperties(it) }

        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val validated = if (Build.VERSION.SDK_INT >= 23) {
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } else {
            hasInternet
        }
        val connected = caps != null

        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
            else -> "unknown"
        }

        return NetworkSnapshot(
            online = connected,
            validatedInternet = validated,
            transportLabel = transport,
            ipv4 = ipv4From(props) ?: wifiIpv4Fallback(),
            gateway = gatewayFrom(props) ?: wifiGatewayFallback() ?: dnsRouterHint(props),
        )
    }

    private fun ipv4From(props: LinkProperties?): String? {
        if (props == null) return null
        for (addr in props.linkAddresses) {
            val a = addr.address
            if (a is Inet4Address && !a.isLoopbackAddress) {
                return a.hostAddress
            }
        }
        return null
    }

    private fun gatewayFrom(props: LinkProperties?): String? {
        if (props == null) return null
        var fallback: String? = null
        for (route in props.routes) {
            val gw = route.gateway ?: continue
            if (gw !is Inet4Address || gw.isLoopbackAddress || gw.isAnyLocalAddress) continue
            val host = gw.hostAddress?.takeIf { it.isNotBlank() && it != "0.0.0.0" } ?: continue
            if (route.isDefaultRoute) return host
            if (fallback == null) fallback = host
        }
        return fallback
    }

    @Suppress("DEPRECATION")
    private fun wifiIpv4Fallback(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo?.ipAddress ?: return null
            if (ip == 0) return null
            formatLegacyIp(ip)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiGatewayFallback(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gw = wm.dhcpInfo?.gateway ?: return null
            if (gw == 0) return null
            formatLegacyIp(gw).takeIf { it != "0.0.0.0" }
        } catch (_: Exception) {
            null
        }
    }

    /** Home LANs often advertise the router as the first private DNS. */
    private fun dnsRouterHint(props: LinkProperties?): String? {
        if (props == null) return null
        for (dns in props.dnsServers) {
            if (dns is Inet4Address && dns.isSiteLocalAddress) {
                return dns.hostAddress?.takeIf { it.isNotBlank() && it != "0.0.0.0" }
            }
        }
        return null
    }

    private fun formatLegacyIp(ip: Int): String =
        "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
}
