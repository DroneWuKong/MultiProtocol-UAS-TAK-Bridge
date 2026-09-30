package com.dronewukong.takbridge.mavlink

/** Hardware-independent receive path; only explicit direct-MSP connections may poll. */
class ProtocolRouter(clock: () -> Long = System::currentTimeMillis) {
    enum class Protocol { AUTO_DETECT, MAVLINK, MSP, GHST, UNKNOWN }
    val mavlinkParser = MavlinkGpsParser(clock)
    val mspParser = MspGpsParser(clock)
    val ghstParser = GhstPassthrough(clock)
    var detectedProtocol = Protocol.AUTO_DETECT; private set
    var isLocked = false; private set
    var bytesProcessed = 0L; private set
    var onGpsPosition: ((GpsPosition) -> Unit)? = null
    var onProtocolDetected: ((Protocol) -> Unit)? = null

    init {
        mavlinkParser.onGpsPosition = { receive(Protocol.MAVLINK, it) }
        mspParser.onGpsPosition = { receive(Protocol.MSP, it) }
        ghstParser.onGpsPosition = { receive(Protocol.GHST, it) }
    }

    private fun receive(protocol: Protocol, position: GpsPosition) {
        if (!isLocked) {
            detectedProtocol = protocol; isLocked = true
            onProtocolDetected?.invoke(protocol)
        }
        if (detectedProtocol == protocol) onGpsPosition?.invoke(position)
    }

    fun feed(data: ByteArray, length: Int) {
        bytesProcessed += length
        when (detectedProtocol) {
            Protocol.MAVLINK -> mavlinkParser.feed(data, length)
            Protocol.MSP -> mspParser.feed(data, length)
            Protocol.GHST -> ghstParser.feed(data, length)
            else -> {
                mavlinkParser.feed(data, length)
                mspParser.feed(data, length)
                ghstParser.feed(data, length)
            }
        }
    }

    fun setProtocol(protocol: Protocol) {
        reset()
        detectedProtocol = protocol
        isLocked = protocol != Protocol.AUTO_DETECT && protocol != Protocol.UNKNOWN
        onProtocolDetected?.invoke(protocol)
    }

    fun reset() {
        mavlinkParser.reset(); mspParser.reset(); ghstParser.reset()
        detectedProtocol = Protocol.AUTO_DETECT; isLocked = false; bytesProcessed = 0
    }

    fun getMspGpsRequest(allowPolling: Boolean = false): ByteArray? =
        if (allowPolling && detectedProtocol == Protocol.MSP) mspParser.buildRequest(106) else null

    fun getStatusString(): String {
        val label = if (isLocked) detectedProtocol.name else "Auto"
        if (bytesProcessed == 0L) return "$label · USB open, no bytes — check TAC USB mode / DTR"
        val ghst = ghstParser
        val counts = "${bytesProcessed}B · MAV ${mavlinkParser.framesReceived} · GHST/CRSF ${ghst.framesReceived}"
        return when {
            ghst.rcFramesReceived > 0 && ghst.gpsFramesReceived == 0L -> "$counts · controller channels only; no GPS"
            ghst.gpsFramesReceived > 0 -> "$counts · GPS frames received"
            else -> "$counts · waiting for GPS/fix"
        }
    }
}
