package com.dronewukong.takbridge.mavlink

/**
 * Lightweight GPS position extracted from telemetry.
 * Source-agnostic — populated by MAVLink, MSP, or manual entry.
 */
data class GpsPosition(
    val lat: Double,           // Decimal degrees
    val lon: Double,           // Decimal degrees
    val altMsl: Double,        // Meters above mean sea level (HAE for CoT)
    val groundSpeed: Double,   // m/s
    val heading: Double,       // Degrees 0-360
    val fixType: Int,          // 0=none, 2=2D, 3=3D, 4=DGPS, 5=RTK float, 6=RTK fixed
    val satellites: Int,
    val hdop: Double,          // Horizontal dilution of precision
    val timestampMs: Long = System.currentTimeMillis()
) {
    val hasValidFix: Boolean get() = fixType >= 3 && lat != 0.0 && lon != 0.0
    val fixTypeString: String get() = when (fixType) {
        0 -> "No Fix"
        1 -> "No Fix"
        2 -> "2D"
        3 -> "3D"
        4 -> "DGPS"
        5 -> "RTK Float"
        6 -> "RTK Fixed"
        else -> "Unknown"
    }
}
