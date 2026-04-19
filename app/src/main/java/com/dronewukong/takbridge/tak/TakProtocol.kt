package com.dronewukong.takbridge.tak

/**
 * TAK protocol framing constants.
 *
 * Sourced from takproto/takmessage.proto and takcontrol.proto (ATAK-CIV, GPLv3).
 *
 * TAK Protocol Version 1 wire format:
 *   [magic: 0xBF][length: varint][TakMessage protobuf bytes]
 *
 * Version negotiation via TakControl message:
 *   minProtoVersion / maxProtoVersion fields.
 *   If both read as 0, version 1 is assumed.
 *
 * For TAK Bridge we send raw XML CoT (no protobuf framing) over multicast UDP.
 * For TAK Server TCP connections, framing is required.
 */
object TakProtocol {

    /** Magic byte that starts every framed TAK Protocol message */
    const val MAGIC = 0xBF.toByte()

    /** Current TAK Protocol version supported */
    const val PROTO_VERSION = 1

    /**
     * Frame a protobuf TakMessage byte array for TCP transmission.
     * Format: [0xBF][varint length][protobuf bytes]
     *
     * NOTE: For UDP multicast (port 6969), send raw CoT XML — no framing needed.
     * Framing only required for TAK Server TCP/TLS streams.
     */
    fun frame(protoBytes: ByteArray): ByteArray {
        val lenBytes = encodeVarint(protoBytes.size)
        return ByteArray(1 + lenBytes.size + protoBytes.size).also { buf ->
            buf[0] = MAGIC
            lenBytes.copyInto(buf, 1)
            protoBytes.copyInto(buf, 1 + lenBytes.size)
        }
    }

    /**
     * Frame a raw XML CoT string for TCP transmission.
     * ATAK accepts raw XML (not protobuf) when sent over TCP/TLS to a TAK Server,
     * but the framing magic byte is still required.
     *
     * In practice, for TAK Bridge we send raw XML without framing over UDP multicast,
     * and use this only for direct TCP connections.
     */
    fun frameXml(xml: String): ByteArray {
        val bytes = xml.toByteArray(Charsets.UTF_8)
        return frame(bytes)
    }

    /** Encode a positive integer as protobuf varint bytes */
    private fun encodeVarint(value: Int): ByteArray {
        var v = value
        val buf = mutableListOf<Byte>()
        while (v > 0x7F) {
            buf.add(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
        buf.add((v and 0x7F).toByte())
        return buf.toByteArray()
    }

    /** Decode a varint from a byte array starting at offset, returns (value, bytesConsumed) */
    fun decodeVarint(buf: ByteArray, offset: Int): Pair<Int, Int> {
        var result = 0
        var shift = 0
        var i = offset
        while (i < buf.size) {
            val b = buf[i++].toInt() and 0xFF
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) break
            shift += 7
        }
        return Pair(result, i - offset)
    }

    // ─── Endpoint string formats (from contact-rxtx-rules.txt, ATAK-CIV) ─────

    /**
     * Format a direct TCP contact endpoint string.
     * Used in <contact endpoint="..."> attribute.
     * Format: "ip:port:tcp"
     */
    fun tcpEndpoint(ip: String, port: Int = 4242) = "$ip:$port:tcp"

    /**
     * Format a TAK Server streaming endpoint string.
     * Used when contact is known via TAK Server.
     * Format: "*:-1:stcp"
     */
    const val TAK_SERVER_ENDPOINT = "*:-1:stcp"

    /**
     * Format a UDP endpoint string.
     * WARNING: UDP endpoints are unreliable — ATAK won't guarantee delivery.
     * Only use when explicitly needed (e.g. legacy mesh-only networks).
     */
    fun udpEndpoint(ip: String, port: Int = 4242) = "$ip:$port:udp"

    /**
     * Source-reply TCP endpoint.
     * ATAK uses this for unicast broadcast — tells recipients to reply to
     * the sender's source IP (discovered from UDP packet header).
     * Format: "ip:port:srctcp"  (ip is overridden by receiver to sender's IP)
     */
    fun srcTcpEndpoint(ip: String, port: Int = 4242) = "$ip:$port:srctcp"

    // ─── Group (team) roles (from __group.xsd, ATAK-CIV) ─────────────────────

    object GroupRole {
        const val TEAM_MEMBER = "Team Member"
        const val TEAM_LEAD = "Team Lead"
        const val HQ = "HQ"
        const val SNIPER = "Sniper"
        const val MEDIC = "Medic"
        const val FORWARD_OBSERVER = "Forward Observer"
        const val RTO = "RTO"
        const val K9 = "K9"
    }

    /** Standard team colors used in ATAK __group element */
    object GroupColor {
        const val WHITE = "White"
        const val YELLOW = "Yellow"
        const val ORANGE = "Orange"
        const val MAGENTA = "Magenta"
        const val RED = "Red"
        const val MAROON = "Maroon"
        const val PURPLE = "Purple"
        const val DARK_BLUE = "Dark Blue"
        const val BLUE = "Blue"
        const val CYAN = "Cyan"
        const val TEAL = "Teal"
        const val GREEN = "Green"
        const val DARK_GREEN = "Dark Green"
        const val BROWN = "Brown"
    }
}
