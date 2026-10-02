package com.dronewukong.takbridge.transport

import com.dronewukong.takbridge.mavlink.ProtocolRouter.Protocol

/** Protocol selection never implicitly enables serial writes. */
enum class ConnectionProfile(val label: String, val protocol: Protocol, val dtr: Boolean, val pollsMsp: Boolean = false) {
    CONTROLLER_MAVLINK("USB controller · MAVLink transcode", Protocol.MAVLINK, true),
    CONTROLLER_GHST("USB controller · GHST C2/Telemetry", Protocol.GHST, true),
    PASSIVE_AUTO("Other USB · passive auto-detect", Protocol.AUTO_DETECT, false),
    DIRECT_MSP("Direct FC · MSP GPS polling", Protocol.MSP, false, true);

    companion object {
        /** Retain upgrade compatibility with previously persisted profile keys. */
        fun fromPersistedName(name: String?): ConnectionProfile = when (name) {
            "TAC_MAVLINK" -> CONTROLLER_MAVLINK
            "TAC_GHST" -> CONTROLLER_GHST
            else -> entries.firstOrNull { it.name == name } ?: CONTROLLER_MAVLINK
        }
    }
}
