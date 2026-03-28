package com.dronewukong.takbridge.transport

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.OutputStreamWriter
import java.net.*
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/**
 * TAK Sender — pushes CoT events to ATAK/WinTAK/iTAK.
 *
 * Three output modes (can run simultaneously):
 *   1. Multicast UDP (239.2.3.1:6969) — zero config, works on any shared network
 *   2. TAK Server TCP — plaintext, port 8087
 *   3. TAK Server TLS — encrypted, port 8089, requires .p12 client cert
 *
 * Features:
 *   - Auto-reconnect on TCP/TLS disconnect (linear backoff)
 *   - Thread-safe send via coroutine scope
 *   - Stats tracking for UI
 */
class TakSender(private val context: Context) {

    companion object {
        private const val TAG = "TakSender"
        private const val TCP_CONNECT_TIMEOUT = 5000
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private var multicastSocket: MulticastSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var tcpSocket: Socket? = null
    private var tcpWriter: OutputStreamWriter? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectJob: Job? = null

    var config: TakConfig = TakConfig()
        private set

    // TLS cert path (loaded from ConfigStore or file picker)
    var tlsCertPath: String = ""
    var tlsCertPassword: String = ""

    // Stats
    var multicastSentCount: Long = 0; private set
    var tcpSentCount: Long = 0; private set
    var lastError: String? = null; private set
    var isMulticastConnected: Boolean = false; private set
    var isTcpConnected: Boolean = false; private set
    private var reconnectAttempts = 0

    // Callbacks
    var onStatusChanged: (() -> Unit)? = null

    fun updateConfig(newConfig: TakConfig) {
        config = newConfig
    }

    fun start() {
        if (config.multicastEnabled) startMulticast()
        if (config.tcpEnabled && config.tcpHost.isNotBlank()) startTcp()
    }

    fun stop() {
        reconnectJob?.cancel()
        scope.coroutineContext.cancelChildren()
        stopMulticast()
        stopTcp()
        reconnectAttempts = 0
    }

    fun send(cotXml: String) {
        if (config.multicastEnabled && isMulticastConnected) sendMulticast(cotXml)
        if (config.tcpEnabled && isTcpConnected) sendTcp(cotXml)
    }

    // ── Multicast ──────────────────────────────────────────────

    private fun startMulticast() {
        scope.launch {
            try {
                val wifiManager = context.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as WifiManager
                multicastLock = wifiManager.createMulticastLock("TAKBridge").apply {
                    setReferenceCounted(true)
                    acquire()
                }
                multicastSocket = MulticastSocket(config.multicastPort).apply {
                    reuseAddress = true
                    timeToLive = 32
                    loopbackMode = false
                }
                isMulticastConnected = true
                lastError = null
                Log.i(TAG, "Multicast ready on ${config.multicastAddress}:${config.multicastPort}")
                withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
            } catch (e: Exception) {
                isMulticastConnected = false
                lastError = "Multicast: ${e.message}"
                Log.e(TAG, "Multicast start failed", e)
                withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
            }
        }
    }

    private fun sendMulticast(cotXml: String) {
        scope.launch {
            try {
                val data = cotXml.toByteArray(Charsets.UTF_8)
                val address = InetAddress.getByName(config.multicastAddress)
                val packet = DatagramPacket(data, data.size, address, config.multicastPort)
                multicastSocket?.send(packet)
                multicastSentCount++
            } catch (e: Exception) {
                Log.e(TAG, "Multicast send failed", e)
                lastError = "MC send: ${e.message}"
            }
        }
    }

    private fun stopMulticast() {
        try {
            multicastSocket?.close()
            multicastLock?.release()
        } catch (_: Exception) {}
        multicastSocket = null
        multicastLock = null
        isMulticastConnected = false
        onStatusChanged?.invoke()
    }

    // ── TCP / TLS ──────────────────────────────────────────────

    private fun startTcp() {
        scope.launch {
            try {
                tcpSocket = if (config.useTls) createTlsSocket() else createPlainSocket()
                tcpWriter = OutputStreamWriter(tcpSocket!!.getOutputStream(), Charsets.UTF_8)
                isTcpConnected = true
                lastError = null
                reconnectAttempts = 0
                val mode = if (config.useTls) "TLS" else "TCP"
                Log.i(TAG, "$mode connected to ${config.tcpHost}:${config.tcpPort}")
                withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
                monitorTcp()
            } catch (e: Exception) {
                isTcpConnected = false
                val mode = if (config.useTls) "TLS" else "TCP"
                lastError = "$mode: ${e.message}"
                Log.e(TAG, "$mode connect failed", e)
                withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
                scheduleReconnect()
            }
        }
    }

    private fun createPlainSocket(): Socket {
        return Socket().apply {
            connect(InetSocketAddress(config.tcpHost, config.tcpPort), TCP_CONNECT_TIMEOUT)
            soTimeout = 10000
        }
    }

    /**
     * Create TLS socket using .p12 client certificate.
     * TAK Server uses mutual TLS — client must present a PKCS12 cert.
     * The .p12 typically contains both the client cert and the server CA trust chain.
     */
    private fun createTlsSocket(): SSLSocket {
        val sslContext = SSLContext.getInstance("TLSv1.2")

        if (tlsCertPath.isNotBlank()) {
            val keyStore = KeyStore.getInstance("PKCS12")
            FileInputStream(tlsCertPath).use { fis ->
                keyStore.load(fis, tlsCertPassword.toCharArray())
            }

            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            kmf.init(keyStore, tlsCertPassword.toCharArray())

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)

            sslContext.init(kmf.keyManagers, tmf.trustManagers, null)
        } else {
            sslContext.init(null, null, null)
        }

        val factory = sslContext.socketFactory
        val socket = factory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(config.tcpHost, config.tcpPort), TCP_CONNECT_TIMEOUT)
        socket.soTimeout = 10000
        socket.startHandshake()
        Log.i(TAG, "TLS handshake: ${socket.session.protocol} ${socket.session.cipherSuite}")
        return socket
    }

    private fun sendTcp(cotXml: String) {
        scope.launch {
            try {
                tcpWriter?.apply {
                    write(cotXml)
                    flush()
                }
                tcpSentCount++
            } catch (e: Exception) {
                Log.e(TAG, "TCP send failed", e)
                lastError = "Send: ${e.message}"
                isTcpConnected = false
                withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
                scheduleReconnect()
            }
        }
    }

    private suspend fun monitorTcp() {
        while (isActive) {
            delay(15_000)
            if (tcpSocket?.isConnected != true || tcpSocket?.isClosed == true) {
                isTcpConnected = false
                withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
                scheduleReconnect()
                break
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            lastError = "Gave up after $MAX_RECONNECT_ATTEMPTS attempts"
            return
        }
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempts++
            val delay = RECONNECT_DELAY_MS * reconnectAttempts
            lastError = "Reconnecting ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)..."
            withContext(Dispatchers.Main) { onStatusChanged?.invoke() }
            delay(delay)
            stopTcp()
            startTcp()
        }
    }

    private fun stopTcp() {
        try {
            tcpWriter?.close()
            tcpSocket?.close()
        } catch (_: Exception) {}
        tcpWriter = null
        tcpSocket = null
        isTcpConnected = false
        onStatusChanged?.invoke()
    }
}
