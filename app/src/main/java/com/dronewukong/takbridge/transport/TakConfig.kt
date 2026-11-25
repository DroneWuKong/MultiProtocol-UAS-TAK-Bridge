package com.dronewukong.takbridge.transport

/**
 * TAK output configuration.
 */
data class TakConfig(
    // Multicast (mesh/local — no server needed)
    val multicastEnabled: Boolean = true,
    val multicastAddress: String = "239.2.3.1",
    val multicastPort: Int = 6969,

    // TAK Server (TCP/TLS)
    val tcpEnabled: Boolean = false,
    val tcpHost: String = "",
    val tcpPort: Int = 8087,
    val useTls: Boolean = false,

    // Identity
    val callsign: String = "DRONE-01",
    val uid: String = "TAKBridge-${java.util.UUID.randomUUID()}",
    val cotType: String = "a-f-A-M-H-Q",

    // Timing
    val updateIntervalMs: Long = 1000, // How often to push CoT
    val staleSeconds: Int = 30,        // How long before TAK marks stale

    // Explicit opt-in: this address is distributed to all recipients of the marker.
    val videoEnabled: Boolean = false,
    val videoUrl: String = ""
)
