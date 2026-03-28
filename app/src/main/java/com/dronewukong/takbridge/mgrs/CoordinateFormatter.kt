package com.dronewukong.takbridge.mgrs

import mil.nga.mgrs.MGRS
import mil.nga.mgrs.grid.GridType
import mil.nga.mgrs.utm.UTM
import kotlin.math.abs

/**
 * Multi-format Coordinate Formatter
 *
 * Supports:
 *   - MGRS      (18S UJ 23371 06519)
 *   - Lat/Lon   Decimal Degrees (38.897800 / -77.036500)
 *   - Lat/Lon   DMS (38°53'52.1"N 077°02'11.4"W)
 *   - UTM       (18S 323371 4306519)
 *
 * CoT always uses decimal degrees internally — this is display only.
 * Tap the coordinate on screen to cycle through formats.
 */
object CoordinateFormatter {

    enum class Format(val label: String) {
        MGRS("MGRS"),
        LATLON_DD("Lat/Lon DD"),
        LATLON_DMS("Lat/Lon DMS"),
        UTM("UTM");

        fun next(): Format {
            val values = entries.toTypedArray()
            return values[(ordinal + 1) % values.size]
        }
    }

    /**
     * Format coordinates for the primary (large) display.
     */
    fun formatPrimary(lat: Double, lon: Double, format: Format): String {
        return try {
            when (format) {
                Format.MGRS -> formatMgrs(lat, lon)
                Format.LATLON_DD -> formatDecimalDegrees(lat, lon)
                Format.LATLON_DMS -> formatDms(lat, lon)
                Format.UTM -> formatUtm(lat, lon)
            }
        } catch (e: Exception) {
            "INVALID"
        }
    }

    /**
     * Format coordinates for the secondary (small) display.
     * Shows the "other" format as context.
     */
    fun formatSecondary(lat: Double, lon: Double, format: Format): String {
        return try {
            when (format) {
                // When primary is MGRS, show lat/lon DD as secondary
                Format.MGRS -> formatDecimalDegrees(lat, lon)
                // When primary is anything else, show MGRS as secondary
                else -> formatMgrs(lat, lon)
            }
        } catch (e: Exception) {
            "--"
        }
    }

    // ── MGRS ───────────────────────────────────────────────────

    private fun formatMgrs(lat: Double, lon: Double): String {
        val point = mil.nga.grid.features.Point.point(lon, lat)
        val mgrs = MGRS.from(point)
        val raw = mgrs.coordinate(GridType.TEN_METER)
        return formatMgrsForDisplay(raw)
    }

    /**
     * "18SUJ2337106519" → "18S UJ 23371 06519"
     */
    private fun formatMgrsForDisplay(mgrsString: String): String {
        if (mgrsString.length < 5) return mgrsString
        val gzd = mgrsString.substring(0, 3)
        val sqId = mgrsString.substring(3, 5)
        val numeric = mgrsString.substring(5)
        val half = numeric.length / 2
        val easting = numeric.substring(0, half)
        val northing = numeric.substring(half)
        return "$gzd $sqId $easting $northing"
    }

    // ── Decimal Degrees ────────────────────────────────────────

    private fun formatDecimalDegrees(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "${"%.6f".format(abs(lat))}°$ns  ${"%.6f".format(abs(lon))}°$ew"
    }

    // ── DMS (Degrees Minutes Seconds) ──────────────────────────

    private fun formatDms(lat: Double, lon: Double): String {
        val latDms = toDms(lat)
        val lonDms = toDms(lon)
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return "${latDms}${ns}  ${lonDms}${ew}"
    }

    private fun toDms(decimal: Double): String {
        val abs = abs(decimal)
        val deg = abs.toInt()
        val minFull = (abs - deg) * 60
        val min = minFull.toInt()
        val sec = (minFull - min) * 60
        return "%d°%02d'%04.1f\"".format(deg, min, sec)
    }

    // ── UTM ────────────────────────────────────────────────────

    private fun formatUtm(lat: Double, lon: Double): String {
        val point = mil.nga.grid.features.Point.point(lon, lat)
        val utm = UTM.from(point)
        return "${utm.zone}${utm.hemisphere.name.first()} ${"%.0f".format(utm.easting)}E ${"%.0f".format(utm.northing)}N"
    }

    // ── Legacy compat (used by CoT formatter, always needs MGRS) ──

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

    fun formatForDisplay(mgrsString: String): String {
        if (mgrsString == "INVALID" || mgrsString.length < 5) return mgrsString
        return try {
            formatMgrsForDisplay(mgrsString)
        } catch (e: Exception) {
            mgrsString
        }
    }
}
