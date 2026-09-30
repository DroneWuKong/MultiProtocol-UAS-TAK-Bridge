package com.dronewukong.takbridge.mavlink

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** GHST and CRSF share framing, not GPS message IDs. No radio writes or guessed MSP tunnel. */
class GhstPassthrough(private val clock: () -> Long = System::currentTimeMillis) {
    private val pending = ArrayList<Byte>()
    private var primary: GpsPosition? = null
    private var secondaryAt: Long? = null
    private var fix = false
    private var sats = 0
    private var speed = -1.0
    private var course = -1.0
    var onGpsPosition: ((GpsPosition) -> Unit)? = null
    var framesReceived = 0L; private set
    var gpsFramesReceived = 0L; private set
    var rcFramesReceived = 0L; private set
    var crcErrors = 0L; private set

    fun reset() {
        pending.clear(); primary = null; secondaryAt = null
        fix = false; sats = 0; speed = -1.0; course = -1.0
        framesReceived = 0; gpsFramesReceived = 0; rcFramesReceived = 0; crcErrors = 0
    }

    fun feed(data: ByteArray, length: Int) {
        require(length in 0..data.size)
        for (i in 0 until length) { pending.add(data[i]); drain() }
    }

    private fun drain() {
        while (pending.size >= 2) {
            val addr = pending[0].toInt() and 255
            val len = pending[1].toInt() and 255
            if (!(addr in 0x80..0x89 || addr in listOf(0xC8, 0xEA, 0xEC, 0xEE)) || len !in 2..62) {
                pending.removeAt(0); continue
            }
            if (pending.size < len + 2) return
            val frame = pending.take(len + 2).toByteArray()
            if (crc8(frame.copyOfRange(2, frame.lastIndex)) != (frame.last().toInt() and 255)) {
                crcErrors++; pending.removeAt(0); continue
            }
            repeat(len + 2) { pending.removeAt(0) }
            framesReceived++
            val type = frame[2].toInt() and 255
            val p = frame.copyOfRange(3, frame.lastIndex)
            if (addr in 0x80..0x89) parseGhst(type, p)
            else if (type == 0x02 && p.size == 15) parseCrsf(p)
        }
    }

    private fun parseGhst(type: Int, p: ByteArray) {
        if (type in 0x10..0x12 && p.size == 10) rcFramesReceived++
        if (p.size != 10) return
        val b = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
        when (type) {
            0x25 -> {
                gpsFramesReceived++
                primary = GpsPosition(b.int / 1e7, b.int / 1e7, b.short.toDouble(),
                    -1.0, -1.0, 0, 0, -1.0, clock())
                emitGhst()
            }
            0x26 -> {
                gpsFramesReceived++
                speed = (b.short.toInt() and 65535) / 100.0
                course = (b.short.toInt() and 65535) / 10.0
                sats = b.get().toInt() and 255
                fix = (p[9].toInt() and 1) != 0
                secondaryAt = clock()
                emitGhst()
            }
        }
    }

    private fun emitGhst() {
        val pos = primary ?: return
        val now = clock()
        val secondaryFresh = secondaryAt?.let { now - it in 0..3000 } == true
        // Secondary/RC traffic must not refresh an old coordinate's timestamp.
        onGpsPosition?.invoke(pos.copy(
            groundSpeed = if (secondaryFresh) speed else -1.0,
            heading = if (secondaryFresh && course < 360) course else -1.0,
            satellites = if (secondaryFresh) sats else 0,
            fixType = if (secondaryFresh && fix && now - pos.timestampMs in 0..3000) 3 else 0
        ))
    }

    private fun parseCrsf(p: ByteArray) {
        val b = ByteBuffer.wrap(p).order(ByteOrder.BIG_ENDIAN)
        val lat = b.int / 1e7; val lon = b.int / 1e7
        val velocity = (b.short.toInt() and 65535) / 36.0
        val heading = (b.short.toInt() and 65535) / 100.0
        val altitude = (b.short.toInt() and 65535) - 1000.0
        val satellites = b.get().toInt() and 255
        gpsFramesReceived++
        // CRSF has no explicit fix-status field; its satellite count is the evidence.
        onGpsPosition?.invoke(GpsPosition(lat, lon, altitude, velocity,
            if (heading < 360) heading else -1.0,
            if (satellites >= 4) 3 else 0, satellites, -1.0, clock()))
    }

    companion object {
        fun crc8(data: ByteArray): Int {
            var crc = 0
            for (b in data) {
                crc = crc xor (b.toInt() and 255)
                repeat(8) { crc = if (crc and 128 != 0) ((crc shl 1) xor 0xD5) and 255 else (crc shl 1) and 255 }
            }
            return crc
        }
    }
}
