package com.dronewukong.takbridge.mavlink

/**
 * Lightweight GPS position extracted from telemetry.
 * Source-agnostic — populated by MAVLink, MSP, or manual entry.
 */
data class GpsPosition(
    val lat: Double,           // Decimal degrees
    val lon: Double,           // Decimal degrees
    val altMsl: Double,        // Meters above mean sea level; not automatically HAE
    val groundSpeed: Double,   // m/s
    val heading: Double,       // Degrees 0-360
    val fixType: Int,          // 0=none, 2=2D, 3=3D, 4=DGPS, 5=RTK float, 6=RTK fixed
    val satellites: Int,
    val hdop: Double,          // Horizontal dilution of precision
    val timestampMs: Long = System.currentTimeMillis()
) {
    val hasValidFix: Boolean get() = fixType in 3..8 && lat.isFinite() && lon.isFinite() &&
        lat in -90.0..90.0 && lon in -180.0..180.0 && altMsl.isFinite()
    fun isFresh(nowMs: Long = System.currentTimeMillis(), maxAgeMs: Long = 5000): Boolean =
        nowMs - timestampMs in 0..maxAgeMs
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
