package com.dronewukong.takbridge.mavlink

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * MSP GPS Parser — extracts GPS from Betaflight/iNav flight controllers.
 *
 * Handles:
 *   - MSP v1: $M< / $M> framing
 *   - MSP v2: $X< / $X> framing
 *   - MSP_RAW_GPS (106): lat, lon, alt, speed, heading, sats
 *   - MSP_COMP_GPS (107): distance/direction to home (bonus data)
 *
 * GHST (Ghost) protocol wraps MSP frames in CRSF-style framing,
 * so the GhstPassthrough strips that layer and feeds raw MSP here.
 */
class MspGpsParser {

    companion object {
        // MSP framing
        private const val MSP_V1_HEADER_IN = '$'.code.toByte()
        private const val MSP_V1_M = 'M'.code.toByte()
        private const val MSP_V2_X = 'X'.code.toByte()
        private const val MSP_DIR_FROM_FC = '>'.code.toByte()

        // MSP command IDs
        private const val MSP_RAW_GPS: Int = 106
        private const val MSP_COMP_GPS: Int = 107

        // Request commands (send to FC to poll GPS)
        const val MSP_REQUEST_RAW_GPS: ByteArray = byteArrayOf(
            '$'.code.toByte(), 'M'.code.toByte(), '<'.code.toByte(),
            0x00, // payload length
            0x6A, // MSP_RAW_GPS = 106
            0x6A  // checksum (XOR of len + cmd)
        )
    }

    private val buffer = ByteArray(256)
    private var bufferPos = 0

    private enum class ParseState {
        IDLE, HEADER_M_X, HEADER_DIR, V1_LEN, V1_CMD, V1_PAYLOAD, V1_CRC,
        V2_FLAG, V2_CMD_LO, V2_CMD_HI, V2_LEN_LO, V2_LEN_HI, V2_PAYLOAD, V2_CRC
    }

    private var state = ParseState.IDLE
    private var isV2 = false
    private var payloadLen = 0
    private var cmdId = 0
    private var payloadPos = 0
    private var crc = 0

    // Callback — same GpsPosition as MAVLink uses
    var onGpsPosition: ((GpsPosition) -> Unit)? = null

    // Home distance/direction from MSP_COMP_GPS
    var homeDistance: Int = 0; private set
    var homeDirection: Int = 0; private set

    /**
     * Feed raw bytes. Handles MSP v1 and v2 framing.
     */
    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            processByte(data[i])
        }
    }

    /**
     * Build an MSP v1 request frame for a given command ID.
     */
    fun buildRequest(cmd: Int): ByteArray {
        val len: Byte = 0
        val cmdByte = cmd.toByte()
        val crc = (len.toInt() xor cmdByte.toInt()).toByte()
        return byteArrayOf(
            '$'.code.toByte(), 'M'.code.toByte(), '<'.code.toByte(),
            len, cmdByte, crc
        )
    }

    private fun processByte(b: Byte) {
        when (state) {
            ParseState.IDLE -> {
                if (b == MSP_V1_HEADER_IN) state = ParseState.HEADER_M_X
            }
            ParseState.HEADER_M_X -> {
                when (b) {
                    MSP_V1_M -> { isV2 = false; state = ParseState.HEADER_DIR }
                    MSP_V2_X -> { isV2 = true; state = ParseState.HEADER_DIR }
                    else -> reset()
                }
            }
            ParseState.HEADER_DIR -> {
                if (b == MSP_DIR_FROM_FC) {
                    state = if (isV2) ParseState.V2_FLAG else ParseState.V1_LEN
                } else {
                    // Response from FC ('>') — we're reading, not sending
                    // Also handle '<' for requests we might see echo'd
                    if (isV2) state = ParseState.V2_FLAG else state = ParseState.V1_LEN
                }
            }

            // ── MSP v1 ────────────────────────────────────
            ParseState.V1_LEN -> {
                payloadLen = b.toInt() and 0xFF
                crc = payloadLen
                state = ParseState.V1_CMD
            }
            ParseState.V1_CMD -> {
                cmdId = b.toInt() and 0xFF
                crc = crc xor cmdId
                payloadPos = 0
                state = if (payloadLen > 0) ParseState.V1_PAYLOAD else ParseState.V1_CRC
            }
            ParseState.V1_PAYLOAD -> {
                buffer[payloadPos++] = b
                crc = crc xor (b.toInt() and 0xFF)
                if (payloadPos >= payloadLen) state = ParseState.V1_CRC
            }
            ParseState.V1_CRC -> {
                if ((b.toInt() and 0xFF) == (crc and 0xFF)) {
                    handleMessage(cmdId, buffer, payloadLen)
                }
                reset()
            }

            // ── MSP v2 ────────────────────────────────────
            ParseState.V2_FLAG -> {
                crc = crc8DvbS2(0, b)
                state = ParseState.V2_CMD_LO
            }
            ParseState.V2_CMD_LO -> {
                cmdId = b.toInt() and 0xFF
                crc = crc8DvbS2(crc, b)
                state = ParseState.V2_CMD_HI
            }
            ParseState.V2_CMD_HI -> {
                cmdId = cmdId or ((b.toInt() and 0xFF) shl 8)
                crc = crc8DvbS2(crc, b)
                state = ParseState.V2_LEN_LO
            }
            ParseState.V2_LEN_LO -> {
                payloadLen = b.toInt() and 0xFF
                crc = crc8DvbS2(crc, b)
                state = ParseState.V2_LEN_HI
            }
            ParseState.V2_LEN_HI -> {
                payloadLen = payloadLen or ((b.toInt() and 0xFF) shl 8)
                crc = crc8DvbS2(crc, b)
                payloadPos = 0
                state = if (payloadLen > 0) ParseState.V2_PAYLOAD else ParseState.V2_CRC
            }
            ParseState.V2_PAYLOAD -> {
                buffer[payloadPos++] = b
                crc = crc8DvbS2(crc, b)
                if (payloadPos >= payloadLen) state = ParseState.V2_CRC
            }
            ParseState.V2_CRC -> {
                if ((b.toInt() and 0xFF) == (crc and 0xFF)) {
                    handleMessage(cmdId, buffer, payloadLen)
                }
                reset()
            }
        }
    }

    private fun handleMessage(cmd: Int, payload: ByteArray, len: Int) {
        when (cmd) {
            MSP_RAW_GPS -> parseRawGps(payload, len)
            MSP_COMP_GPS -> parseCompGps(payload, len)
        }
    }

    /**
     * MSP_RAW_GPS (106)
     * Offset  Size  Field
     * 0       1     fix_type (0=none, 1=fix, 2=GPS)
     * 1       1     numSat
     * 2       4     lat (1/10000000 deg)
     * 6       4     lon (1/10000000 deg)
     * 10      2     altitude (meters)
     * 12      2     groundSpeed (cm/s)
     * 14      2     groundCourse (degrees * 10)
     * 16      2     hdop (optional, iNav)
     */
    private fun parseRawGps(payload: ByteArray, len: Int) {
        if (len < 16) return

        val buf = ByteBuffer.wrap(payload, 0, len).order(ByteOrder.LITTLE_ENDIAN)

        val fixType = buf.get().toInt() and 0xFF
        val sats = buf.get().toInt() and 0xFF
        val lat = buf.getInt() / 1e7
        val lon = buf.getInt() / 1e7
        val alt = buf.getShort().toInt().toDouble() // meters
        val speed = (buf.getShort().toInt() and 0xFFFF) / 100.0 // cm/s → m/s
        val course = (buf.getShort().toInt() and 0xFFFF) / 10.0 // deg*10 → deg

        // HDOP if available (iNav sends 18 bytes)
        val hdop = if (len >= 18) {
            buf.getShort().toInt().toDouble() / 100.0
        } else {
            -1.0
        }

        // MSP fix types: 0=none, 1=fix, 2=GPS fix
        // Map to MAVLink-compatible: 0=none, 2=2D, 3=3D
        val mappedFix = when (fixType) {
            0 -> 0
            1 -> 2 // basic fix → 2D
            2 -> 3 // GPS fix → 3D
            else -> fixType
        }

        onGpsPosition?.invoke(
            GpsPosition(
                lat = lat,
                lon = lon,
                altMsl = alt,
                groundSpeed = speed,
                heading = course,
                fixType = mappedFix,
                satellites = sats,
                hdop = hdop
            )
        )
    }

    /**
     * MSP_COMP_GPS (107)
     * 0  2  distanceToHome (meters)
     * 2  2  directionToHome (degrees)
     * 4  1  GPS update flag
     */
    private fun parseCompGps(payload: ByteArray, len: Int) {
        if (len < 5) return
        val buf = ByteBuffer.wrap(payload, 0, len).order(ByteOrder.LITTLE_ENDIAN)
        homeDistance = buf.getShort().toInt() and 0xFFFF
        homeDirection = buf.getShort().toInt() and 0xFFFF
    }

    private fun reset() {
        state = ParseState.IDLE
        bufferPos = 0
        payloadPos = 0
        crc = 0
    }

    /**
     * CRC8 DVB-S2 used by MSP v2.
     */
    private fun crc8DvbS2(crcIn: Int, b: Byte): Int {
        var crc = crcIn xor (b.toInt() and 0xFF)
        for (i in 0 until 8) {
            crc = if (crc and 0x80 != 0) {
                (crc shl 1) xor 0xD5
            } else {
                crc shl 1
            }
        }
        return crc and 0xFF
    }
}
