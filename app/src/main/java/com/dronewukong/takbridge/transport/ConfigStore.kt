package com.dronewukong.takbridge.transport

import android.content.Context
import android.content.SharedPreferences
import com.dronewukong.takbridge.mavlink.ProtocolRouter

/**
 * Persists TAK Bridge configuration across app restarts.
 *
 * Saves: callsign, TAK Server IP/port, multicast toggle,
 * baud rate, last detected protocol, CoT type, update interval.
 *
 * No encryption needed — no secrets here. TAK Server certs
 * are handled separately via the Android keystore.
 */
object ConfigStore {

    private const val PREFS_NAME = "tak_bridge_config"

    // Keys
    private const val KEY_CALLSIGN = "callsign"
    private const val KEY_UID = "uid"
    private const val KEY_COT_TYPE = "cot_type"
    private const val KEY_MULTICAST_ENABLED = "multicast_enabled"
    private const val KEY_MULTICAST_ADDRESS = "multicast_address"
    private const val KEY_MULTICAST_PORT = "multicast_port"
    private const val KEY_TCP_ENABLED = "tcp_enabled"
    private const val KEY_TCP_HOST = "tcp_host"
    private const val KEY_TCP_PORT = "tcp_port"
    private const val KEY_TCP_USE_TLS = "tcp_use_tls"
    private const val KEY_UPDATE_INTERVAL = "update_interval_ms"
    private const val KEY_STALE_SECONDS = "stale_seconds"
    private const val KEY_BAUD_RATE = "baud_rate"
    private const val KEY_PROTOCOL = "protocol"
    private const val KEY_TLS_CERT_PATH = "tls_cert_path"
    private const val KEY_TLS_CERT_PASSWORD = "tls_cert_password"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveTakConfig(context: Context, config: TakConfig) {
        prefs(context).edit().apply {
            putString(KEY_CALLSIGN, config.callsign)
            putString(KEY_UID, config.uid)
            putString(KEY_COT_TYPE, config.cotType)
            putBoolean(KEY_MULTICAST_ENABLED, config.multicastEnabled)
            putString(KEY_MULTICAST_ADDRESS, config.multicastAddress)
            putInt(KEY_MULTICAST_PORT, config.multicastPort)
            putBoolean(KEY_TCP_ENABLED, config.tcpEnabled)
            putString(KEY_TCP_HOST, config.tcpHost)
            putInt(KEY_TCP_PORT, config.tcpPort)
            putBoolean(KEY_TCP_USE_TLS, config.useTls)
            putLong(KEY_UPDATE_INTERVAL, config.updateIntervalMs)
            putInt(KEY_STALE_SECONDS, config.staleSeconds)
            apply()
        }
    }

    fun loadTakConfig(context: Context): TakConfig {
        val p = prefs(context)
        return TakConfig(
            callsign = p.getString(KEY_CALLSIGN, "DRONE-01") ?: "DRONE-01",
            uid = p.getString(KEY_UID, "TAKBridge-${System.currentTimeMillis() % 100000}") ?: "TAKBridge-0",
            cotType = p.getString(KEY_COT_TYPE, "a-f-A-M-H-Q") ?: "a-f-A-M-H-Q",
            multicastEnabled = p.getBoolean(KEY_MULTICAST_ENABLED, true),
            multicastAddress = p.getString(KEY_MULTICAST_ADDRESS, "239.2.3.1") ?: "239.2.3.1",
            multicastPort = p.getInt(KEY_MULTICAST_PORT, 6969),
            tcpEnabled = p.getBoolean(KEY_TCP_ENABLED, false),
            tcpHost = p.getString(KEY_TCP_HOST, "") ?: "",
            tcpPort = p.getInt(KEY_TCP_PORT, 8087),
            useTls = p.getBoolean(KEY_TCP_USE_TLS, false),
            updateIntervalMs = p.getLong(KEY_UPDATE_INTERVAL, 1000),
            staleSeconds = p.getInt(KEY_STALE_SECONDS, 30)
        )
    }

    fun saveBaudRate(context: Context, baud: Int) {
        prefs(context).edit().putInt(KEY_BAUD_RATE, baud).apply()
    }

    fun loadBaudRate(context: Context): Int {
        return prefs(context).getInt(KEY_BAUD_RATE, 115200)
    }

    fun saveProtocol(context: Context, protocol: ProtocolRouter.Protocol) {
        prefs(context).edit().putString(KEY_PROTOCOL, protocol.name).apply()
    }

    fun loadProtocol(context: Context): ProtocolRouter.Protocol {
        val name = prefs(context).getString(KEY_PROTOCOL, "AUTO_DETECT") ?: "AUTO_DETECT"
        return try {
            ProtocolRouter.Protocol.valueOf(name)
        } catch (e: Exception) {
            ProtocolRouter.Protocol.AUTO_DETECT
        }
    }

    fun saveTlsCertPath(context: Context, path: String, password: String) {
        prefs(context).edit()
            .putString(KEY_TLS_CERT_PATH, path)
            .putString(KEY_TLS_CERT_PASSWORD, password)
            .apply()
    }

    fun loadTlsCertPath(context: Context): Pair<String, String> {
        val p = prefs(context)
        return Pair(
            p.getString(KEY_TLS_CERT_PATH, "") ?: "",
            p.getString(KEY_TLS_CERT_PASSWORD, "") ?: ""
        )
    }
}
