package com.dronewukong.takbridge.cot

import com.dronewukong.takbridge.mavlink.GpsPosition
import java.text.SimpleDateFormat
import java.util.*

/**
 * Cursor on Target (CoT) Event Formatter
 *
 * Generates CoT XML events per the CoT schema used by TAK (ATAK/WinTAK/iTAK).
 * CoT is the lingua franca of military SA — everything speaks it.
 *
 * Key fields:
 *   - uid:   Unique ID for this track (persists across updates)
 *   - type:  MIL-STD-2525 symbology code
 *   - time:  When the event was generated
 *   - start: When the position was valid
 *   - stale: When the position expires (TAK grays out stale tracks)
 *   - point: lat/lon/hae (height above ellipsoid) + CE/LE (circular/linear error)
 *
 * The <detail> block carries callsign, speed, heading, and other metadata.
 */
object CotFormatter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Build a CoT event XML string from a GPS position.
     *
     * @param position GPS data from the drone
     * @param uid Unique track identifier (e.g., "DRONE-01")
     * @param callsign Display name on TAK map (e.g., "Wingman-1")
     * @param cotType CoT type string — see CotTypes
     * @param staleSeconds How long before TAK marks this track stale
     * @return Complete CoT XML event string
     */
    fun buildEvent(
        position: GpsPosition,
        uid: String,
        callsign: String,
        cotType: String = CotTypes.DEFAULT,
        staleSeconds: Int = 30
    ): String {
        val now = Date()
        val staleTime = Date(now.time + staleSeconds * 1000L)

        val time = dateFormat.format(now)
        val start = dateFormat.format(now)
        val stale = dateFormat.format(staleTime)

        // CE90 (circular error 90%) — rough estimate from HDOP
        // CE90 ≈ HDOP * 2.5m for typical GPS
        val ce = if (position.hdop > 0) position.hdop * 2.5 else 15.0
        val le = 99999.0 // Linear error — unknown without baro calibration

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$uid'")
            append(" type='$cotType'")
            append(" time='$time'")
            append(" start='$start'")
            append(" stale='$stale'")
            append(" how='m-g'") // machine-GPS
            append(">")

            // Point — this is what shows up on the TAK map
            append("<point")
            append(" lat='${position.lat}'")
            append(" lon='${position.lon}'")
            append(" hae='${position.altMsl}'") // Height Above Ellipsoid
            append(" ce='$ce'")
            append(" le='$le'")
            append("/>")

            // Detail — metadata that TAK uses for display and SA
            append("<detail>")

            // Contact — this sets the callsign label on the map
            append("<contact callsign='$callsign'/>")

            // Track — speed and heading for TAK's velocity leader
            if (position.groundSpeed >= 0) {
                append("<track")
                append(" speed='${position.groundSpeed}'") // m/s
                append(" course='${position.heading}'")    // degrees
                append("/>")
            }

            // Remarks — extra info visible on tap
            append("<remarks>")
            append("Fix: ${position.fixTypeString}")
            if (position.satellites >= 0) append(" | Sats: ${position.satellites}")
            if (position.hdop >= 0) append(" | HDOP: ${"%.1f".format(position.hdop)}")
            append(" | TAK Bridge by AI Wingman")
            append("</remarks>")

            // Precision location for TAK
            append("<precisionlocation")
            append(" geopointsrc='GPS'")
            append(" altsrc='GPS'")
            append("/>")

            // __group for team color/role (optional but nice)
            append("<__group name='Cyan' role='Team Member'/>")

            append("</detail>")
            append("</event>")
        }
    }

    /**
     * Build a simple ping/SA event (heartbeat) to keep the track alive.
     * Lighter weight than a full position update.
     */
    fun buildPing(uid: String, callsign: String): String {
        val now = Date()
        val stale = Date(now.time + 60_000L) // 60s stale for pings

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$uid'")
            append(" type='t-x-c-t'") // Ping type
            append(" time='${dateFormat.format(now)}'")
            append(" start='${dateFormat.format(now)}'")
            append(" stale='${dateFormat.format(stale)}'")
            append(" how='h-e'") // human-estimated
            append(">")
            append("<point lat='0' lon='0' hae='0' ce='999999' le='999999'/>")
            append("<detail><contact callsign='$callsign'/></detail>")
            append("</event>")
        }
    }
}
