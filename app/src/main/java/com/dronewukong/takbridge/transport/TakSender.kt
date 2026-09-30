package com.dronewukong.takbridge.transport

import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import java.net.*
import javax.net.ssl.SSLSocket

/** One writer per output, bounded fresh-only queues, and isolated connection generations. */
class TakSender(@Suppress("UNUSED_PARAMETER") context: Context) {
    private data class Message(val xml: String, val deadline: Long)
    private class Session(val config: TakConfig) {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val udp = Channel<Message>(1, BufferOverflow.DROP_OLDEST)
        val tcp = Channel<Message>(1, BufferOverflow.DROP_OLDEST)
        @Volatile var active = true
        @Volatile var socket: Socket? = null
        @Volatile var datagram: DatagramSocket? = null
        fun close() {
            active = false
            scope.cancel()
            udp.close(); tcp.close()
            try { socket?.close() } catch (_: Exception) { }
            datagram?.close()
        }
    }
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var session: Session? = null
    var config = TakConfig(); private set
    var tlsCertPath = ""
    var tlsCertPassword = ""
    var tlsCaPath = ""
    @Volatile var multicastSentCount = 0L; private set
    @Volatile var tcpSentCount = 0L; private set
    @Volatile var lastError: String? = null; private set
    @Volatile var isMulticastConnected = false; private set
    @Volatile var isTcpConnected = false; private set
    var onStatusChanged: (() -> Unit)? = null

    fun updateConfig(newConfig: TakConfig) {
        require(newConfig.tcpPort in 1..65535 && newConfig.multicastPort in 1..65535) { "Port must be 1–65535" }
        require(newConfig.updateIntervalMs >= 100) { "Update interval too short" }
        config = newConfig
    }

    fun start() {
        stop()
        lastError = null
        val s = Session(config)
        session = s
        val clientPath = tlsCertPath; val password = tlsCertPassword; val caPath = tlsCaPath
        if (s.config.multicastEnabled) s.scope.launch {
            try {
                val address = InetAddress.getByName(s.config.multicastAddress)
                require(address.isMulticastAddress) { "Destination is not a multicast address" }
                // Sending must not bind ATAK's receive port on the same phone.
                MulticastSocket().use { socket ->
                    s.datagram = socket
                    socket.timeToLive = 32
                    if (!current(s)) return@launch
                    isMulticastConnected = true; notify(s)
                    for (message in s.udp) {
                        if (!current(s) || message.deadline < System.currentTimeMillis()) continue
                        val bytes = message.xml.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(bytes, bytes.size, address, s.config.multicastPort))
                        if (current(s)) { multicastSentCount++; notify(s) }
                    }
                }
            } catch (e: Exception) {
                if (current(s)) { isMulticastConnected = false; lastError = "Multicast: ${e.message}"; notify(s) }
            }
        }
        if (s.config.tcpEnabled && s.config.tcpHost.isNotBlank()) s.scope.launch {
            var attempts = 0
            while (isActive && current(s)) {
                var reader: Job? = null
                try {
                    val socket = if (s.config.useTls) {
                        TlsCredentials.context(clientPath, password, caPath).socketFactory.createSocket()
                    } else Socket()
                    s.socket = socket
                    socket.use {
                        if (!current(s)) return@launch
                        socket.connect(InetSocketAddress(s.config.tcpHost, s.config.tcpPort), 5000)
                        socket.keepAlive = true
                        socket.soTimeout = 10000
                        if (socket is SSLSocket) {
                            socket.sslParameters = socket.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                            socket.startHandshake()
                        }
                        if (!current(s)) return@launch
                        isTcpConnected = true; lastError = null; attempts = 0; notify(s)
                        // Detect peer EOF even while GPS/output is idle. Incoming CoT is discarded.
                        reader = s.scope.launch {
                            try {
                                val buffer = ByteArray(4096)
                                while (isActive && current(s)) {
                                    try { if (socket.getInputStream().read(buffer) < 0) break }
                                    catch (_: SocketTimeoutException) { continue }
                                }
                            } catch (_: Exception) { }
                            finally { try { socket.close() } catch (_: Exception) { } }
                        }
                        val out = socket.getOutputStream()
                        while (isActive && current(s) && !socket.isClosed) {
                            val message = withTimeoutOrNull(250) { s.tcp.receive() } ?: continue
                            if (message.deadline < System.currentTimeMillis()) continue
                            out.write(message.xml.toByteArray(Charsets.UTF_8)); out.flush()
                            if (current(s)) { tcpSentCount++; notify(s) }
                        }
                    }
                    if (current(s)) lastError = "TAK server closed the connection"
                } catch (e: Exception) {
                    if (current(s)) lastError = "${if (s.config.useTls) "TLS" else "TCP"}: ${e.message}"
                } finally {
                    reader?.cancel()
                    try { s.socket?.close() } catch (_: Exception) { }
                    if (current(s)) { isTcpConnected = false; notify(s) }
                }
                if (!current(s)) break
                attempts++
                delay((1000L * attempts).coerceAtMost(15000))
            }
        }
    }

    fun stop() {
        val old = session
        session = null
        old?.close()
        isMulticastConnected = false; isTcpConnected = false; lastError = null
        onStatusChanged?.invoke()
    }

    /** A reconnect never flushes a backlog of stale aircraft positions. */
    fun send(cotXml: String, validUntilMs: Long = System.currentTimeMillis() + 1000) {
        val s = session ?: return
        if (validUntilMs < System.currentTimeMillis()) return
        val message = Message(cotXml, validUntilMs)
        if (s.config.multicastEnabled) s.udp.trySend(message)
        if (s.config.tcpEnabled) s.tcp.trySend(message)
    }
    private fun current(s: Session) = session === s && s.active
    private fun notify(s: Session) { main.post { if (current(s)) onStatusChanged?.invoke() } }
}
