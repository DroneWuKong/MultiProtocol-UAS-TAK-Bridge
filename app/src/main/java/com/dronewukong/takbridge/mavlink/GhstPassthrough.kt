package com.dronewukong.takbridge.mavlink

/**
 * GHST (Ghost Protocol) Passthrough Parser
 *
 * GHST uses CRSF-style framing for telemetry:
 *   [dest_addr] [frame_len] [frame_type] [payload...] [crc8]
 *
 * GPS data comes in two ways over GHST:
 *   1. Native GHST GPS frames (frame_type 0x08/0x09) — parsed directly
 *   2. MSP passthrough frames (frame_type 0x7F) — stripped and forwarded to MspGpsParser
 *
 * This handles the IRONghost JR module → Jumper T20S → USB → S25 path.
 */
class GhstPassthrough {

    companion object {
        // GHST/CRSF frame types
        private const val GHST_ADDR_FC: Byte = 0xC8.toByte()       // Flight controller address
        private const val GHST_ADDR_MODULE: Byte = 0xEE.toByte()   // TX module address

        // GHST native GPS frame types
        private const val GHST_GPS_PRIMARY: Int = 0x08              // Primary GPS data
        private const val GHST_GPS_SECONDARY: Int = 0x09            // Secondary GPS data

        // MSP passthrough over GHST/CRSF
        private const val GHST_MSP_REQ: Int = 0x7A                 // MSP request wrapper
        private const val GHST_MSP_RESP: Int = 0x7B                // MSP response wrapper
        private const val CRSF_MSP_REQ: Int = 0x7A
        private const val CRSF_MSP_RESP: Int = 0x7B

        // GHST native telemetry
        private const val GHST_FRAME_PACK: Int = 0x11              // Battery/RSSI pack
    }

    private val buffer = ByteArray(256)
    private var bufferPos = 0

    private enum class State { WAIT_ADDR, WAIT_LEN, PAYLOAD }
    private var state = State.WAIT_ADDR
    private var frameLen = 0
    private var payloadPos = 0

    // Callback for extracted MSP bytes — feed these into MspGpsParser
    var onMspData: ((ByteArray, Int) -> Unit)? = null

    // Callback for native GHST GPS — direct position extraction
    var onGpsPosition: ((GpsPosition) -> Unit)? = null

    // Protocol detection
    var framesReceived: Long = 0; private set
    var gpsFramesReceived: Long = 0; private set

    /**
     * Feed raw bytes from USB serial.
     */
    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            processByte(data[i])
        }
    }

    private fun processByte(b: Byte) {
        when (state) {
            State.WAIT_ADDR -> {
                // Look for valid GHST/CRSF destination addresses
                if (b == GHST_ADDR_FC || b == GHST_ADDR_MODULE ||
                    b == 0xEA.toByte() /* broadcast */) {
                    buffer[0] = b
                    bufferPos = 1
                    state = State.WAIT_LEN
                }
            }
            State.WAIT_LEN -> {
                frameLen = b.toInt() and 0xFF
                if (frameLen < 2 || frameLen > 64) {
                    // Invalid frame length — reset
                    state = State.WAIT_ADDR
                    return
                }
                buffer[1] = b
                bufferPos = 2
                payloadPos = 0
                state = State.PAYLOAD
            }
            State.PAYLOAD -> {
                buffer[bufferPos++] = b
                payloadPos++

                // frameLen includes type + payload + crc
                if (payloadPos >= frameLen) {
                    parseFrame()
                    state = State.WAIT_ADDR
                }
            }
        }
    }

    private fun parseFrame() {
        framesReceived++

        // Frame type is at offset 2 (after addr + len)
        val frameType = buffer[2].toInt() and 0xFF

        // Payload starts at offset 3, CRC is last byte
        val payloadStart = 3
        val payloadLen = frameLen - 2 // subtract type byte and CRC

        // TODO: CRC8 validation — skip for now, prioritize getting data flowing

        when (frameType) {
            GHST_GPS_PRIMARY -> parseGhstGpsPrimary(payloadStart, payloadLen)
            GHST_GPS_SECONDARY -> parseGhstGpsSecondary(payloadStart, payloadLen)
            GHST_MSP_RESP, CRSF_MSP_RESP -> extractMspPayload(payloadStart, payloadLen)
        }
    }

    /**
     * GHST GPS Primary frame (0x08)
     *
     * Offset  Size  Field
     * 0       4     Latitude (degrees * 1e7, signed)
     * 4       4     Longitude (degrees * 1e7, signed)
     * 8       2     Altitude (meters - 1000m offset)
     */
    private fun parseGhstGpsPrimary(offset: Int, len: Int) {
        if (len < 10) return
        gpsFramesReceived++

        val lat = readInt32LE(offset) / 1e7
        val lon = readInt32LE(offset + 4) / 1e7
        val alt = (readUInt16LE(offset + 8) - 1000).toDouble()

        // Primary frame gives position — merge with secondary if available
        onGpsPosition?.invoke(
            GpsPosition(
                lat = lat,
                lon = lon,
                altMsl = alt,
                groundSpeed = lastSpeed,
                heading = lastHeading,
                fixType = if (lat != 0.0 || lon != 0.0) 3 else 0,
                satellites = lastSats,
                hdop = -1.0
            )
        )
    }

    // Cached values from secondary frame for merging
    private var lastSpeed = 0.0
    private var lastHeading = 0.0
    private var lastSats = 0

    /**
     * GHST GPS Secondary frame (0x09)
     *
     * Offset  Size  Field
     * 0       2     Ground speed (km/h * 10)
     * 2       2     Ground course (degrees * 10)
     * 4       1     Number of satellites
     * 5       2     Home distance (meters)
     * 7       2     Home direction (degrees)
     * 9       1     Flags
     */
    private fun parseGhstGpsSecondary(offset: Int, len: Int) {
        if (len < 6) return
        gpsFramesReceived++

        lastSpeed = readUInt16LE(offset) / 36.0  // km/h*10 → m/s
        lastHeading = readUInt16LE(offset + 2) / 10.0
        lastSats = buffer[offset + 4].toInt() and 0xFF
    }

    /**
     * Extract MSP payload from CRSF/GHST MSP passthrough frame.
     * Strip the CRSF wrapper, forward raw MSP bytes.
     */
    private fun extractMspPayload(offset: Int, len: Int) {
        if (len < 1) return

        // The MSP frame is embedded in the CRSF payload
        // First byte might be origin/dest addressing, skip to MSP data
        val mspStart = offset
        val mspLen = len

        if (mspLen > 0) {
            val mspData = ByteArray(mspLen)
            System.arraycopy(buffer, mspStart, mspData, 0, mspLen)
            onMspData?.invoke(mspData, mspLen)
        }
    }

    // ── Little-endian helpers ──────────────────────────────────

    private fun readInt32LE(offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 8) or
                ((buffer[offset + 2].toInt() and 0xFF) shl 16) or
                ((buffer[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun readUInt16LE(offset: Int): Int {
        return (buffer[offset].toInt() and 0xFF) or
                ((buffer[offset + 1].toInt() and 0xFF) shl 8)
    }
}
