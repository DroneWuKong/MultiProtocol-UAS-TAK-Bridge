package com.dronewukong.takbridge.mavlink

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal MAVLink v2 parser — extracts GPS position only.
 *
 * We only care about:
 *   - MSG_ID 24: GPS_RAW_INT (lat, lon, alt, fix, sats, hdop)
 *   - MSG_ID 33: GLOBAL_POSITION_INT (lat, lon, alt, heading, velocities)
 *
 * This is NOT a full MAVLink library — it's a scalpel, not a Swiss army knife.
 * For the full stack, see Wingman Buddy's MavlinkProtocolHandler.
 */
class MavlinkGpsParser {

    companion object {
        // MAVLink v2 magic byte
        private const val MAVLINK_V2_MAGIC: Byte = 0xFD.toByte()

        // Message IDs we care about
        private const val MSG_GPS_RAW_INT = 24
        private const val MSG_GLOBAL_POSITION_INT = 33

        // MAVLink v2 header size (magic + len + incompat + compat + seq + sysid + compid + msgid*3)
        private const val HEADER_SIZE = 10
    }

    private val buffer = ByteArray(300) // Max MAVLink v2 frame = 280 bytes
    private var bufferPos = 0

    // Callback
    var onGpsPosition: ((GpsPosition) -> Unit)? = null

    /**
     * Feed raw bytes from USB serial. Parser handles framing.
     */
    fun feed(data: ByteArray, length: Int) {
        for (i in 0 until length) {
            processByte(data[i])
        }
    }

    private fun processByte(b: Byte) {
        if (bufferPos == 0 && b != MAVLINK_V2_MAGIC) {
            return // Wait for start of frame
        }

        buffer[bufferPos++] = b

        // Need at least header to know payload length
        if (bufferPos < HEADER_SIZE) return

        val payloadLen = buffer[1].toInt() and 0xFF
        val frameLen = HEADER_SIZE + payloadLen + 2 // +2 for checksum (skip signature)

        if (bufferPos < frameLen) return

        // We have a complete frame — parse it
        parseFrame(payloadLen)
        bufferPos = 0
    }

    private fun parseFrame(payloadLen: Int) {
        // Message ID is 3 bytes little-endian at offset 7
        val msgId = (buffer[7].toInt() and 0xFF) or
                ((buffer[8].toInt() and 0xFF) shl 8) or
                ((buffer[9].toInt() and 0xFF) shl 16)

        val payload = ByteBuffer.wrap(buffer, HEADER_SIZE, payloadLen)
            .order(ByteOrder.LITTLE_ENDIAN)

        when (msgId) {
            MSG_GPS_RAW_INT -> parseGpsRawInt(payload)
            MSG_GLOBAL_POSITION_INT -> parseGlobalPositionInt(payload)
        }
    }

    /**
     * GPS_RAW_INT (ID 24)
     * Offset  Type     Field
     * 0       uint64   time_usec
     * 8       int32    lat (degE7)
     * 12      int32    lon (degE7)
     * 16      int32    alt (mm MSL)
     * 20      uint16   eph (HDOP * 100)
     * 22      uint16   epv
     * 24      uint16   vel (cm/s)
     * 26      uint16   cog (cdeg)
     * 28      uint8    fix_type
     * 29      uint8    satellites_visible
     */
    private fun parseGpsRawInt(payload: ByteBuffer) {
        if (payload.remaining() < 30) return

        payload.getLong()  // time_usec — skip
        val lat = payload.getInt() / 1e7
        val lon = payload.getInt() / 1e7
        val alt = payload.getInt() / 1000.0
        val hdop = (payload.getShort().toInt() and 0xFFFF) / 100.0
        payload.getShort() // epv — skip
        val vel = (payload.getShort().toInt() and 0xFFFF) / 100.0
        val cog = (payload.getShort().toInt() and 0xFFFF) / 100.0
        val fixType = payload.get().toInt() and 0xFF
        val sats = payload.get().toInt() and 0xFF

        onGpsPosition?.invoke(
            GpsPosition(
                lat = lat,
                lon = lon,
                altMsl = alt,
                groundSpeed = vel,
                heading = cog,
                fixType = fixType,
                satellites = sats,
                hdop = hdop
            )
        )
    }

    /**
     * GLOBAL_POSITION_INT (ID 33)
     * Offset  Type     Field
     * 0       uint32   time_boot_ms
     * 4       int32    lat (degE7)
     * 8       int32    lon (degE7)
     * 12      int32    alt (mm MSL)
     * 16      int32    relative_alt (mm AGL)
     * 20      int16    vx (cm/s)
     * 22      int16    vy (cm/s)
     * 24      int16    vz (cm/s)
     * 26      uint16   hdg (cdeg)
     */
    private fun parseGlobalPositionInt(payload: ByteBuffer) {
        if (payload.remaining() < 28) return

        payload.getInt() // time_boot_ms — skip
        val lat = payload.getInt() / 1e7
        val lon = payload.getInt() / 1e7
        val alt = payload.getInt() / 1000.0
        payload.getInt() // relative_alt — skip
        val vx = payload.getShort() / 100.0
        val vy = payload.getShort() / 100.0
        payload.getShort() // vz — skip
        val hdg = (payload.getShort().toInt() and 0xFFFF) / 100.0

        val groundSpeed = Math.sqrt(vx * vx + vy * vy)

        onGpsPosition?.invoke(
            GpsPosition(
                lat = lat,
                lon = lon,
                altMsl = alt,
                groundSpeed = groundSpeed,
                heading = hdg,
                fixType = 3, // GLOBAL_POSITION_INT implies at least 3D fix
                satellites = -1, // Not in this message
                hdop = -1.0
            )
        )
    }
}
