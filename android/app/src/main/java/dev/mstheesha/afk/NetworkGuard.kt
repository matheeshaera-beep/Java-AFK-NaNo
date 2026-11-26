package dev.mstheesha.afk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager

/**
 * Keeps [EngineHost] informed about network availability (respecting Wi-Fi-only
 * guardrail) and stops bots cleanly if unplugged battery drops below the user's
 * battery-guard threshold.
 */
class NetworkGuard(private val context: Context) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private var registered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = evaluateNetwork()
        override fun onLost(network: Network) = evaluateNetwork()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = evaluateNetwork()
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            val threshold = Prefs.batteryGuardPercent(appContext)
            if (threshold <= 0 || !AppGraph.hasActiveSessions()) return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            if (level < 0 || charging) return
            val pct = (level * 100) / scale
            if (pct <= threshold) {
                AlertNotifier.notify(
                    appContext,
                    "Java AFK",
                    "Battery guard ($pct%)",
                    "Stopped all running bots because battery reached $pct%.",
                    colorHex = 0xE65100,
                )
                AppGraph.stopAllSessions()
            }
        }
    }

    fun start() {
        if (registered) return
        registered = true
        runCatching { cm?.registerDefaultNetworkCallback(networkCallback) }
        runCatching { appContext.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        evaluateNetwork()
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { cm?.unregisterNetworkCallback(networkCallback) }
        runCatching { appContext.unregisterReceiver(batteryReceiver) }
    }

    fun evaluateNetwork() {
        val manager = cm
        val active = manager?.activeNetwork
        val caps = if (manager != null && active != null) manager.getNetworkCapabilities(active) else null
        val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        val wifiOnly = Prefs.wifiOnly(appContext)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val allowed = hasInternet && (!wifiOnly || onWifi)
        EngineHost.setNetworkAvailable(allowed)
    }
}
