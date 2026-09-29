package com.dronewukong.takbridge.transport

import com.dronewukong.takbridge.mavlink.ProtocolRouter.Protocol

/** Protocol selection never implicitly enables serial writes. */
enum class ConnectionProfile(val label: String, val protocol: Protocol, val dtr: Boolean, val pollsMsp: Boolean = false) {
    TAC_MAVLINK("TAC.CTRL · MAVLink transcode", Protocol.MAVLINK, true),
    TAC_GHST("TAC.CTRL · GHST C2/Telemetry", Protocol.GHST, true),
    PASSIVE_AUTO("Other USB · passive auto-detect", Protocol.AUTO_DETECT, false),
    DIRECT_MSP("Direct FC · MSP GPS polling", Protocol.MSP, false, true)
}
