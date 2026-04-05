package com.dronewukong.takbridge.ui.bridge

import android.content.Context
import android.location.LocationManager
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * JS bridge injected into the Tools WebView.
 * Exposes native Android capabilities to the tools JS environment.
 *
 * Usage in tools JS:
 *   Android.getGpsLocation()  → JSON {lat, lon, accuracy, altitude, provider}
 *   Android.log(msg)          → logcat tag WingmanTools
 *   Android.getAppVersion()   → "wingman-buddy/android"
 */
class WingmanJsBridge(private val context: Context) {

    @JavascriptInterface
    fun getGpsLocation(): String {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                "fused"
            )
            val loc = providers.firstNotNullOfOrNull { provider ->
                try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
            }
            if (loc != null) {
                """{"lat":${loc.latitude},"lon":${loc.longitude},"accuracy":${loc.accuracy},"altitude":${loc.altitude},"provider":"${loc.provider}"}"""
            } else {
                """{"error":"no_fix"}"""
            }
        } catch (e: Exception) {
            Log.w("WingmanBridge", "GPS: ${e.message}")
            """{"error":"${e.message?.replace("\"","\\\"")}"}"""
        }
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.i("WingmanTools", msg)
    }

    @JavascriptInterface
    fun getAppVersion(): String = "wingman-buddy/android"
}
