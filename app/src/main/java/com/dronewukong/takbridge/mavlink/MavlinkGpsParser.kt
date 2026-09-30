package com.dronewukong.takbridge.mavlink

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.hypot

/** Bounded MAVLink 1/2 observer. CRC checked; signatures consumed, not authenticated. */
class MavlinkGpsParser(private val clock: () -> Long = System::currentTimeMillis) {
    private val pending = ArrayList<Byte>()
    private var source: Pair<Int, Int>? = null
    private var rawGps: GpsPosition? = null
    var onGpsPosition: ((GpsPosition) -> Unit)? = null
    var framesReceived = 0L; private set
    var crcErrors = 0L; private set
    var signedFrames = 0L; private set

    fun reset() {
        pending.clear(); source = null; rawGps = null
        framesReceived = 0; crcErrors = 0; signedFrames = 0
    }

    fun feed(data: ByteArray, length: Int) {
        require(length in 0..data.size)
        for (i in 0 until length) { pending.add(data[i]); drain() }
    }

    private fun drain() {
        while (pending.isNotEmpty()) {
            val magic = pending[0].toInt() and 255
            if (magic != 0xFD && magic != 0xFE) { pending.removeAt(0); continue }
            val v2 = magic == 0xFD
            val header = if (v2) 10 else 6
            if (pending.size < header) return
            val size = pending[1].toInt() and 255
            val flags = if (v2) pending[2].toInt() and 255 else 0
            if (flags and 0xFE != 0) { pending.removeAt(0); continue }
            val signed = flags and 1 != 0
            val total = header + size + 2 + if (signed) 13 else 0
            if (pending.size < total) return
            val frame = pending.take(total).toByteArray()
            val id = if (v2) (frame[7].toInt() and 255) or
                ((frame[8].toInt() and 255) shl 8) or ((frame[9].toInt() and 255) shl 16)
                else frame[5].toInt() and 255
            val extra = when (id) { 0 -> 50; 24 -> 24; 33 -> 104; else -> null }
            if (extra != null) {
                val actual = (frame[header + size].toInt() and 255) or ((frame[header + size + 1].toInt() and 255) shl 8)
                if (checksum(frame.copyOfRange(1, header + size), extra) != actual) {
                    crcErrors++; pending.removeAt(0); continue
                }
            }
            repeat(total) { pending.removeAt(0) }
            if (extra == null) continue
            val minimum = when (id) { 0 -> 9; 24 -> 30; else -> 28 }
            if ((!v2 && size != minimum) || (v2 && size < 1)) continue
            framesReceived++
            if (signed) signedFrames++
            if (id == 0) continue // Heartbeats never refresh GPS.
            val identity = (frame[if (v2) 5 else 3].toInt() and 255) to
                (frame[if (v2) 6 else 4].toInt() and 255)
            if (identity.first == 0 || identity.second == 0) continue
            if (source == null) source = identity
            if (source != identity) continue
            // MAVLink 2 strips trailing zero bytes, including core fields.
            val payload = frame.copyOfRange(header, header + size).copyOf(maxOf(minimum, size))
            val b = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            if (id == 24) parseRaw(b) else parseGlobal(b)
        }
    }

    private fun parseRaw(b: ByteBuffer) {
        b.long
        val lat = b.int / 1e7; val lon = b.int / 1e7; val altitude = b.int / 1000.0
        val hdop = unsignedMeasurement(b.short, 100.0)
        b.short
        val speed = unsignedMeasurement(b.short, 100.0)
        val heading = unsignedMeasurement(b.short, 100.0).let { if (it < 360) it else -1.0 }
        val fix = b.get().toInt() and 255
        val satellites = (b.get().toInt() and 255).let { if (it == 255) -1 else it }
        val pos = GpsPosition(lat, lon, altitude, speed, heading, fix, satellites, hdop, clock())
        rawGps = pos
        onGpsPosition?.invoke(pos)
    }

    private fun parseGlobal(b: ByteBuffer) {
        b.int
        val lat = b.int / 1e7; val lon = b.int / 1e7; val altitude = b.int / 1000.0
        b.int
        val vx = b.short / 100.0; val vy = b.short / 100.0
        b.short
        val heading = unsignedMeasurement(b.short, 100.0).let { if (it < 360) it else -1.0 }
        val fix = rawGps?.takeIf { it.hasValidFix && clock() - it.timestampMs in 0..3000 }
        // GLOBAL_POSITION_INT alone contains no GPS fix status.
        onGpsPosition?.invoke(GpsPosition(lat, lon, altitude, hypot(vx, vy), heading,
            fix?.fixType ?: 0, fix?.satellites ?: -1, fix?.hdop ?: -1.0, clock()))
    }

    private fun unsignedMeasurement(value: Short, divisor: Double): Double =
        (value.toInt() and 65535).let { if (it == 65535) -1.0 else it / divisor }

    companion object {
        fun checksum(data: ByteArray, extra: Int): Int {
            var crc = 0xFFFF
            for (value in data.map { it.toInt() and 255 } + extra) {
                var tmp = value xor (crc and 255)
                tmp = (tmp xor (tmp shl 4)) and 255
                crc = ((crc shr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp shr 4)) and 65535
            }
            return crc
        }
    }
}
