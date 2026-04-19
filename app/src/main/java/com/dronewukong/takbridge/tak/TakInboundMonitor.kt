package com.dronewukong.takbridge.tak

import android.util.Log
import com.dronewukong.takbridge.cot.CotTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * Monitors multicast for incoming CoT events from ATAK.
 *
 * Key event types to watch (sourced from ATAK-CIV):
 *
 *   b-a-*     Emergency beacon — operator in distress; trigger RTH/loiter
 *   j-m       Joint tasking message
 *   b-m-p-s-p-loc  SPI from ATAK operator (they're pointing the camera)
 *
 * Emergency type prefix from EmergencyDetailHandler.java (ATAK-CIV):
 *   All types starting with "b-a" trigger emergency handling.
 *   Cancel is sent with same UID + cancel="true" in <emergency> detail.
 *
 * Contact routing from contact-rxtx-rules.txt (ATAK-CIV):
 *   UDP discovery: parse <contact endpoint="ip:port:type"> to learn peer addresses.
 *   If endpoint type is "udp": peer is at ip:6969 (SA) / ip:17012 (chat)
 *   If endpoint type is "tcp": peer is at ip:port (direct TCP)
 */
class TakInboundMonitor(
    private val scope: CoroutineScope,
    private val onEmergency: (uid: String, callsign: String, type: String, cancel: Boolean) -> Unit,
    private val onSpi: (uid: String, lat: Double, lon: Double) -> Unit,
    private val onPeerDiscovered: (uid: String, callsign: String, endpoint: String) -> Unit
) {
    private val TAG = "TakInboundMonitor"
    private var monitorJob: Job? = null

    fun start(
        multicastGroup: String = CotTypes.MULTICAST_GROUP,
        port: Int = CotTypes.UDP_PORT_SA
    ) {
        monitorJob = scope.launch(Dispatchers.IO) {
            val group = InetAddress.getByName(multicastGroup)
            val buf = ByteArray(65535)
            var socket: MulticastSocket? = null
            try {
                socket = MulticastSocket(port).apply {
                    joinGroup(group)
                    soTimeout = 2000
                }
                Log.i(TAG, "Listening on $multicastGroup:$port")
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buf, buf.size)
                        socket.receive(packet)
                        val xml = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        parseInbound(xml, packet.address.hostAddress ?: "")
                    } catch (_: java.net.SocketTimeoutException) {
                        // normal — keep looping
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Monitor error: ${e.message}")
            } finally {
                try { socket?.leaveGroup(group) } catch (_: Exception) {}
                socket?.close()
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }

    private fun parseInbound(xml: String, srcIp: String) {
        try {
            val type = xmlAttr(xml, "type") ?: return
            val uid = xmlAttr(xml, "uid") ?: return

            // Emergency detection — b-a prefix (EmergencyDetailHandler.java, ATAK-CIV)
            if (type.startsWith(CotTypes.EMERGENCY_PREFIX)) {
                val callsign = xmlDetailAttr(xml, "contact", "callsign") ?: uid
                val emergencyType = xmlDetailAttr(xml, "emergency", "type") ?: "unknown"
                val cancel = xmlDetailAttr(xml, "emergency", "cancel") == "true"
                Log.w(TAG, "Emergency from $callsign ($uid): type=$emergencyType cancel=$cancel")
                onEmergency(uid, callsign, emergencyType, cancel)
                return
            }

            // SPI from operator — b-m-p-s-p-loc (sensor point of interest)
            if (type == CotTypes.SENSOR_SPI_LOCATION || type == CotTypes.SENSOR_SPI) {
                val lat = xmlAttr(xml, "lat")?.toDoubleOrNull() ?: return
                val lon = xmlAttr(xml, "lon")?.toDoubleOrNull() ?: return
                onSpi(uid, lat, lon)
                return
            }

            // Contact discovery from any SA event — learn peer endpoints
            // contact-rxtx-rules.txt (ATAK-CIV): parse endpoint attr to discover peers
            val callsign = xmlDetailAttr(xml, "contact", "callsign")
            val endpoint = xmlDetailAttr(xml, "contact", "endpoint")
            if (callsign != null && endpoint != null && endpoint.isNotEmpty()) {
                onPeerDiscovered(uid, callsign, endpoint)
            }

        } catch (e: Exception) {
            Log.v(TAG, "Parse error on inbound CoT: ${e.message}")
        }
    }

    // ─── Minimal XML attribute extraction (no full parser needed for CoT) ─────

    private fun xmlAttr(xml: String, attr: String): String? {
        val pattern = Regex("""$attr=['"]([^'"]+)['"]""")
        return pattern.find(xml)?.groupValues?.get(1)
    }

    private fun xmlDetailAttr(xml: String, element: String, attr: String): String? {
        // Find the element, then extract the attribute from it
        val elemPattern = Regex("""<$element\s[^>]*>|<$element\s[^/]*/?>""")
        val match = elemPattern.find(xml) ?: return null
        return xmlAttr(match.value, attr)
    }
}
