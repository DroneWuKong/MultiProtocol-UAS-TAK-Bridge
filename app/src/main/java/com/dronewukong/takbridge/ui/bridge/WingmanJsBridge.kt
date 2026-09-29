package com.dronewukong.takbridge.ui.bridge

import android.Manifest
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.location.Location
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
class WingmanJsBridge(
    private val context: Context,
    private val onSaveReport: (String, String) -> Unit = { _, _ -> }
) {

    @JavascriptInterface
    fun copyText(text: String): Boolean = runCatching {
        require(text.length <= 200_000)
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("TAK Bridge tools", text))
        true
    }.getOrDefault(false)

    @JavascriptInterface
    fun saveReport(filename: String, html: String) {
        if (html.length <= 1_000_000 && filename.matches(Regex("[A-Za-z0-9_-]+\\.html"))) {
            onSaveReport(filename, html)
        }
    }

    @JavascriptInterface
    fun getGpsLocation(): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return """{"error":"location_permission_not_granted"}"""
        }
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                "fused"
            )
            var loc: Location? = null
            for (provider in providers) {
                loc = try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null }
                if (loc != null) break
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
