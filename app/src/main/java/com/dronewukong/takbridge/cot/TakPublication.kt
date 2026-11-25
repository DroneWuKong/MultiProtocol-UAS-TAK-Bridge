package com.dronewukong.takbridge.cot

import com.dronewukong.takbridge.mavlink.GpsPosition
import com.dronewukong.takbridge.transport.TakConfig

/** Source-neutral publication boundary shared by all existing telemetry parsers and local replay. */
data class TakPublication(val xml: String, val validUntilMs: Long) {
    companion object {
        fun build(position: GpsPosition, config: TakConfig, simulation: Boolean, nowMs: Long): TakPublication {
            require(position.hasValidFix && position.isFresh(nowMs)) { "Fresh valid GPS required" }
            require(config.staleSeconds in 1..3600)
            val uid = if (simulation) "TAKBridge-SIMULATION" else config.uid
            val address = if (config.videoEnabled) SharedVideoAddress.validate(config.videoUrl) else ""
            val deadline = minOf(position.timestampMs + 5000, position.timestampMs + config.staleSeconds * 1000L)
            require(deadline > nowMs) { "Event already expired" }
            return TakPublication(CotFormatter.buildDroneSA(uid = uid,
                callsign = if (simulation) "SIMULATION" else config.callsign, pos = position,
                cotType = config.cotType, staleSec = config.staleSeconds, nowMs = nowMs,
                videoUrl = address), deadline)
        }
    }
}
