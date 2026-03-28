package com.dronewukong.takbridge.mgrs

import mil.nga.mgrs.MGRS
import mil.nga.mgrs.grid.GridType

/**
 * MGRS Coordinate Converter
 *
 * Wraps NGA's official MGRS library for lat/lon ↔ MGRS conversion.
 * TAK displays MGRS natively, but CoT protocol uses decimal degrees —
 * so we convert for display and pass raw lat/lon in the CoT XML.
 */
object MgrsConverter {

    /**
     * Convert decimal lat/lon to MGRS string at 10m precision.
     * @param lat Latitude in decimal degrees
     * @param lon Longitude in decimal degrees
     * @return MGRS coordinate string (e.g., "18SUJ2337106519")
     */
    fun toMgrs(lat: Double, lon: Double, precision: Int = 5): String {
        return try {
            val point = mil.nga.grid.features.Point.point(lon, lat)
            val mgrs = MGRS.from(point)
            val gridType = when (precision) {
                0 -> GridType.GZD
                1 -> GridType.HUNDRED_KILOMETER
                2 -> GridType.TEN_KILOMETER
                3 -> GridType.KILOMETER
                4 -> GridType.HUNDRED_METER
                5 -> GridType.TEN_METER
                6 -> GridType.METER
                else -> GridType.TEN_METER
            }
            mgrs.coordinate(gridType)
        } catch (e: Exception) {
            "INVALID"
        }
    }

    /**
     * Convert MGRS string back to decimal lat/lon.
     * @return Pair<latitude, longitude> or null if invalid
     */
    fun fromMgrs(mgrsString: String): Pair<Double, Double>? {
        return try {
            val point = MGRS.parse(mgrsString).toPoint()
            Pair(point.latitude, point.longitude)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Format for display — adds spaces for readability.
     * "18SUJ2337106519" → "18S UJ 23371 06519"
     */
    fun formatForDisplay(mgrsString: String): String {
        if (mgrsString == "INVALID" || mgrsString.length < 5) return mgrsString
        return try {
            val gzd = mgrsString.substring(0, 3)        // 18S
            val sqId = mgrsString.substring(3, 5)        // UJ
            val numeric = mgrsString.substring(5)        // 2337106519
            val half = numeric.length / 2
            val easting = numeric.substring(0, half)     // 23371
            val northing = numeric.substring(half)       // 06519
            "$gzd $sqId $easting $northing"
        } catch (e: Exception) {
            mgrsString
        }
    }
}
