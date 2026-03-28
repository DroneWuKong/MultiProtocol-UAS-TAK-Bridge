package com.dronewukong.takbridge.mavlink

import android.util.Log

/**
 * Protocol Auto-Detector
 *
 * Feeds raw USB serial bytes to all three parsers simultaneously during detection.
 * Once a parser gets a valid GPS fix, we lock to that protocol and stop feeding the others.
 *
 * Detection heuristic:
 *   - MAVLink v2: Starts with 0xFD magic byte
 *   - MSP v1/v2:  Starts with '$M' or '$X'
 *   - GHST/CRSF:  Starts with 0xC8/0xEE address bytes, frame lengths 2-64
 *
 * Can also be manually overridden via setProtocol().
 */
class ProtocolRouter {

    companion object {
        private const val TAG = "ProtocolRouter"
        private const val DETECTION_WINDOW_BYTES = 4096 // Bytes to sample before giving up
    }

    enum class Protocol {
        AUTO_DETECT,
        MAVLINK,
        MSP,
        GHST,
        UNKNOWN
    }

    // Parsers
    val mavlinkParser = MavlinkGpsParser()
    val mspParser = MspGpsParser()
    val ghstParser = GhstPassthrough()

    // State
    var detectedProtocol: Protocol = Protocol.AUTO_DETECT; private set
    var isLocked: Boolean = false; private set
    private var bytesProcessed: Long = 0

    // Stats for detection
    private var mavlinkHits = 0
    private var mspHits = 0
    private var ghstHits = 0

    // Unified GPS callback — whatever protocol wins, same output
    var onGpsPosition: ((GpsPosition) -> Unit)? = null
    var onProtocolDetected: ((Protocol) -> Unit)? = null

    init {
        // Wire all parsers to report GPS through us
        mavlinkParser.onGpsPosition = { pos ->
            mavlinkHits++
            if (!isLocked) lockProtocol(Protocol.MAVLINK)
            if (detectedProtocol == Protocol.MAVLINK) {
                onGpsPosition?.invoke(pos)
            }
        }

        mspParser.onGpsPosition = { pos ->
            mspHits++
            if (!isLocked) lockProtocol(Protocol.MSP)
            if (detectedProtocol == Protocol.MSP) {
                onGpsPosition?.invoke(pos)
            }
        }

        ghstParser.onGpsPosition = { pos ->
            ghstHits++
            if (!isLocked) lockProtocol(Protocol.GHST)
            if (detectedProtocol == Protocol.GHST) {
                onGpsPosition?.invoke(pos)
            }
        }

        // GHST can also carry MSP passthrough — wire it
        ghstParser.onMspData = { data, len ->
            mspParser.feed(data, len)
        }
    }

    /**
     * Feed raw bytes from USB serial.
     * During auto-detect, feeds all parsers.
     * After lock, feeds only the detected parser.
     */
    fun feed(data: ByteArray, length: Int) {
        bytesProcessed += length

        when (detectedProtocol) {
            Protocol.AUTO_DETECT -> {
                // Feed everyone — first valid GPS wins
                mavlinkParser.feed(data, length)
                mspParser.feed(data, length)
                ghstParser.feed(data, length)

                // If we've processed enough bytes without a lock, try heuristics
                if (bytesProcessed > DETECTION_WINDOW_BYTES && !isLocked) {
                    heuristicDetect(data, length)
                }
            }
            Protocol.MAVLINK -> mavlinkParser.feed(data, length)
            Protocol.MSP -> mspParser.feed(data, length)
            Protocol.GHST -> {
                ghstParser.feed(data, length)
                // GHST MSP passthrough is wired in init{}
            }
            Protocol.UNKNOWN -> {
                // Keep trying
                mavlinkParser.feed(data, length)
                mspParser.feed(data, length)
                ghstParser.feed(data, length)
            }
        }
    }

    /**
     * Manually set protocol — bypasses auto-detect.
     */
    fun setProtocol(protocol: Protocol) {
        detectedProtocol = protocol
        isLocked = protocol != Protocol.AUTO_DETECT
        Log.i(TAG, "Protocol manually set to $protocol")
        onProtocolDetected?.invoke(protocol)
    }

    /**
     * Reset to auto-detect mode.
     */
    fun reset() {
        detectedProtocol = Protocol.AUTO_DETECT
        isLocked = false
        bytesProcessed = 0
        mavlinkHits = 0
        mspHits = 0
        ghstHits = 0
    }

    /**
     * Get MSP request bytes for GPS polling (Betaflight/iNav need to be asked).
     * Returns null if not MSP protocol.
     */
    fun getMspGpsRequest(): ByteArray? {
        if (detectedProtocol == Protocol.MSP || detectedProtocol == Protocol.GHST) {
            return mspParser.buildRequest(106) // MSP_RAW_GPS
        }
        return null
    }

    private fun lockProtocol(protocol: Protocol) {
        if (isLocked) return
        detectedProtocol = protocol
        isLocked = true
        Log.i(TAG, "Protocol locked: $protocol (MAV:$mavlinkHits MSP:$mspHits GHST:$ghstHits)")
        onProtocolDetected?.invoke(protocol)
    }

    /**
     * Byte-level heuristic when no GPS fix yet — look at frame markers.
     */
    private fun heuristicDetect(data: ByteArray, length: Int) {
        var mavCount = 0
        var mspCount = 0
        var ghstCount = 0

        for (i in 0 until length) {
            when (data[i]) {
                0xFD.toByte() -> mavCount++
                '$'.code.toByte() -> {
                    if (i + 1 < length) {
                        when (data[i + 1]) {
                            'M'.code.toByte(), 'X'.code.toByte() -> mspCount++
                        }
                    }
                }
                0xC8.toByte(), 0xEE.toByte() -> ghstCount++
            }
        }

        // Don't lock from heuristics alone — just log for debugging
        Log.d(TAG, "Heuristic scan: MAV=$mavCount MSP=$mspCount GHST=$ghstCount " +
                "(total bytes: $bytesProcessed)")
    }

    fun getStatusString(): String {
        return when {
            isLocked -> "${detectedProtocol.name} (locked)"
            detectedProtocol == Protocol.AUTO_DETECT -> "Detecting... (${bytesProcessed}B)"
            else -> "${detectedProtocol.name}"
        }
    }
}
