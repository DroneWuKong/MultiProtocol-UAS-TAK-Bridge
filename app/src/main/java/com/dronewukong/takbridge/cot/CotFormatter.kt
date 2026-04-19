package com.dronewukong.takbridge.cot

import com.dronewukong.takbridge.mavlink.GpsPosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * CoT XML event builder.
 *
 * Detail element schemas sourced from AndroidTacticalAssaultKit-CIV (GPLv3)
 * takcot/xsd/details/ — authoritative ATAK CoT schema definitions.
 *
 * Wire protocol spec from takproto/*.proto (proto3, package atakmap.commoncommo.protobuf.v1).
 *
 * Broadcast port 6969 (SA/non-chat), 17012 (chat) per commoncommo/core/atakcotcaptures.txt.
 */
object CotFormatter {

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Unknown/unset values per CoT spec (use 9999999 for ce/le/hae when unknown)
    private const val UNKNOWN_CE = 9999999.0
    private const val UNKNOWN_LE = 9999999.0
    private const val UNKNOWN_HAE = 9999999.0

    /**
     * Build a standard SA (situational awareness) CoT event for a drone position.
     *
     * Generates: <event> + <point> + <detail> with <contact>, <track>,
     * <precisionlocation>, <status>, <takv>, <uid> children.
     *
     * @param uid        Persistent drone UID (stable across flights)
     * @param callsign   Human-readable callsign shown in ATAK
     * @param pos        GPS position from any protocol parser
     * @param cotType    CoT type — defaults to [CotTypes.UAV_FRIENDLY_ROTARY]
     * @param staleSec   Seconds until marker goes stale in ATAK (default 30)
     * @param battery    Battery percentage 0-100, or null if unknown
     */
    fun buildDroneSA(
        uid: String,
        callsign: String,
        pos: GpsPosition,
        cotType: String = CotTypes.UAV_FRIENDLY_ROTARY,
        staleSec: Int = 30,
        battery: Int? = null,
        endpoint: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val stale = now + (staleSec * 1000L)
        val timeStr = isoFmt.format(Date(now))
        val staleStr = isoFmt.format(Date(stale))

        // CE from HDOP: HDOP * ~5m typical GPS accuracy
        val ce = if (pos.hdop > 0) pos.hdop * 5.0 else UNKNOWN_CE
        val hae = if (pos.altMsl != 0.0) pos.altMsl else UNKNOWN_HAE

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$uid'")
            append(" type='$cotType'")
            append(" time='$timeStr'")
            append(" start='$timeStr'")
            append(" stale='$staleStr'")
            append(" how='m-g'>")  // machine-generated

            // <point> — WGS-84, HAE in meters, ce/le circular/linear error in meters
            append("<point")
            append(" lat='${pos.lat}'")
            append(" lon='${pos.lon}'")
            append(" hae='${String.format("%.2f", hae)}'")
            append(" ce='${String.format("%.1f", ce)}'")
            append(" le='$UNKNOWN_LE'/>")

            append("<detail>")

            // <contact> — callsign + optional direct endpoint
            // endpoint format: "ip:port:tcp" or "*:-1:stcp" for TAK server
            // XSD: contact.xsd — callsign required, endpoint optional
            append("<contact callsign='$callsign'")
            if (endpoint != null) append(" endpoint='$endpoint'")
            append("/>")

            // <uid> — Droid name (display name in ATAK contact list)
            append("<uid Droid='$callsign'/>")

            // <track> — speed in m/s, course in degrees true north
            // XSD: track.xsd — course and speed required
            append("<track")
            append(" course='${String.format("%.2f", pos.heading)}'")
            append(" speed='${String.format("%.2f", pos.groundSpeed)}'/>")

            // <precisionlocation> — source of GPS fix and altitude
            // geopointsrc values: GPS, DGPS, User, Estimated, Simulated
            // altsrc values: GPS, DTED0, DTED1, DTED2, DTED3, LIDAR, User
            append("<precisionlocation")
            append(" geopointsrc='${precisionSrc(pos)}'")
            append(" altsrc='GPS'/>")

            // <status> — battery percentage
            // XSD: status.xsd — battery attribute (0-100)
            if (battery != null) {
                append("<status battery='$battery'/>")
            }

            // <takv> — sender platform identification
            // XSD: takv.xsd — device, platform, os, version all required
            append("<takv")
            append(" device='DroneWuKong TAK Bridge'")
            append(" platform='TAK Bridge'")
            append(" os='Android'")
            append(" version='1.0'/>")

            append("</detail>")
            append("</event>")
        }
    }

    /**
     * Build a Sensor Point of Interest (SPI) CoT event.
     * Used to mark where a drone's camera is looking.
     *
     * Attach to a drone marker via video_spi_uid meta value in ATAK.
     * The SPI marker type b-m-p-s-p-loc triggers ATAK's UAS tool to
     * link the video stream to the position.
     *
     * @param droneUid   UID of the parent drone marker
     * @param spiUid     UID for this SPI (typically droneUid + "-spi")
     * @param callsign   Display name
     * @param lat/lon    Where the camera is pointing on the ground
     * @param hae        Target elevation in meters HAE
     */
    fun buildSPI(
        droneUid: String,
        spiUid: String,
        callsign: String,
        lat: Double,
        lon: Double,
        hae: Double = UNKNOWN_HAE
    ): String {
        val now = System.currentTimeMillis()
        val stale = now + 30_000L
        val timeStr = isoFmt.format(Date(now))
        val staleStr = isoFmt.format(Date(stale))

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$spiUid'")
            append(" type='${CotTypes.SENSOR_SPI_LOCATION}'")
            append(" time='$timeStr'")
            append(" start='$timeStr'")
            append(" stale='$staleStr'")
            append(" how='m-g'>")
            append("<point lat='$lat' lon='$lon' hae='${String.format("%.2f", hae)}'")
            append(" ce='$UNKNOWN_CE' le='$UNKNOWN_LE'/>")
            append("<detail>")
            append("<contact callsign='$callsign SPI'/>")
            append("<uid Droid='$callsign SPI'/>")
            // Link back to parent drone marker
            append("<link uid='$droneUid' type='${CotTypes.UAV_FRIENDLY_ROTARY}'")
            append(" relation='p-p' how='m-g'/>")
            append("</detail>")
            append("</event>")
        }
    }

    /**
     * Build a video alias CoT event.
     * Attaches an RTSP/RTMP stream to a drone marker in ATAK.
     *
     * Detail spec from VideoDetailHandler.java (ATAK-CIV):
     *   WARNING: video_spi_uid and video_sensor_uid are used by the UAS tool.
     *
     * @param droneUid   UID of the parent drone marker
     * @param videoUid   Unique UID for this video connection entry
     * @param callsign   Display name
     * @param rtspUrl    Full RTSP/RTMP/UDP URL
     * @param spiUid     UID of associated SPI marker (optional)
     * @param sensorUid  UID of associated sensor FOV marker (optional)
     */
    fun buildVideoAlias(
        droneUid: String,
        videoUid: String,
        callsign: String,
        rtspUrl: String,
        spiUid: String? = null,
        sensorUid: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val stale = now + 30_000L
        val timeStr = isoFmt.format(Date(now))
        val staleStr = isoFmt.format(Date(stale))

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$droneUid'")
            append(" type='${CotTypes.UAV_FRIENDLY_ROTARY}'")
            append(" time='$timeStr'")
            append(" start='$timeStr'")
            append(" stale='$staleStr'")
            append(" how='m-g'>")
            // Point at 0,0 — video events piggyback on the drone marker's position
            append("<point lat='0.0' lon='0.0' hae='0.0' ce='$UNKNOWN_CE' le='$UNKNOWN_LE'/>")
            append("<detail>")
            // __video element — sourced from VideoDetailHandler.java (ATAK-CIV)
            // uid links to ConnectionEntry in ATAK's VideoManager
            // spi/sensor UIDs link to the SPI and FOV cone markers
            append("<__video")
            append(" url='$rtspUrl'")
            append(" uid='$videoUid'")
            if (spiUid != null) append(" spi='$spiUid'")
            if (sensorUid != null) append(" sensor='$sensorUid'")
            append(" buffer='-1'")
            append(" timeout='5000'/>")
            append("</detail>")
            append("</event>")
        }
    }

    /**
     * Build a Sensor FOV CoT event.
     * Draws a field-of-view cone on the ATAK map centered on the drone.
     *
     * Detail spec from SensorDetailHandler.java (ATAK-CIV):
     *   - azimuth: true north degrees [0, 360)
     *   - fov: visible EDGE to EDGE degrees (NOT center to edge — important!)
     *   - range: max distance of cone in meters (hard max in ATAK is 15000m)
     *   - vfov: vertical FOV in degrees
     *   - roll: camera roll in degrees
     *   - elevation: camera depression angle in degrees
     *
     * @param droneUid   Parent drone marker UID
     * @param sensorUid  UID for the sensor FOV marker (typically droneUid + "-sensor")
     * @param callsign   Display name
     * @param azimuthDeg Camera heading in degrees true north
     * @param hFovDeg    Horizontal FOV edge-to-edge in degrees
     * @param vFovDeg    Vertical FOV in degrees
     * @param rangeM     Max range of FOV cone in meters (clamped to 15000)
     * @param rollDeg    Camera roll in degrees (default 0)
     * @param elevDeg    Depression angle (negative = looking down, default 0)
     */
    fun buildSensorFov(
        droneUid: String,
        sensorUid: String,
        callsign: String,
        azimuthDeg: Double,
        hFovDeg: Double = 90.0,
        vFovDeg: Double = 60.0,
        rangeM: Double = 500.0,
        rollDeg: Double = 0.0,
        elevDeg: Double = 0.0,
        lat: Double,
        lon: Double,
        hae: Double = UNKNOWN_HAE
    ): String {
        val now = System.currentTimeMillis()
        val stale = now + 30_000L
        val timeStr = isoFmt.format(Date(now))
        val staleStr = isoFmt.format(Date(stale))
        val clampedRange = minOf(rangeM, 15000.0)

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$sensorUid'")
            append(" type='${CotTypes.SENSOR_SPI}'")
            append(" time='$timeStr'")
            append(" start='$timeStr'")
            append(" stale='$staleStr'")
            append(" how='m-g'>")
            append("<point lat='$lat' lon='$lon'")
            append(" hae='${String.format("%.2f", hae)}'")
            append(" ce='$UNKNOWN_CE' le='$UNKNOWN_LE'/>")
            append("<detail>")
            append("<contact callsign='$callsign Sensor'/>")
            // sensor element — full schema from SensorDetailHandler.java (ATAK-CIV)
            // NOTE: fov is edge-to-edge, NOT half-angle. A 90-deg camera has fov="90".
            append("<sensor")
            append(" azimuth='${String.format("%.1f", azimuthDeg)}'")
            append(" fov='${String.format("%.1f", hFovDeg)}'")
            append(" vfov='${String.format("%.1f", vFovDeg)}'")
            append(" range='${String.format("%.0f", clampedRange)}'")
            append(" roll='${String.format("%.1f", rollDeg)}'")
            append(" elevation='${String.format("%.1f", elevDeg)}'")
            append(" fovAlpha='0.3'")
            append(" fovRed='0.0' fovGreen='0.8' fovBlue='1.0'")  // teal
            append(" strokeColor='-1'")  // white outline
            append(" strokeWeight='1.0'")
            append(" displayMagneticReference='0'/>")
            // link back to parent drone
            append("<link uid='$droneUid' type='${CotTypes.UAV_FRIENDLY_ROTARY}'")
            append(" relation='p-p' how='m-g'/>")
            append("</detail>")
            append("</event>")
        }
    }

    /**
     * Build a UAV tasking request CoT event using the __services pattern.
     *
     * Sourced from ServicesDetailHandler.java (ATAK-CIV):
     *   The s-t-s-i type with a cot:// sink is how ATAK operators push
     *   ISR tasking to a UAS. Wingman Command can listen for this type
     *   and route it to the autonomy layer.
     *
     * @param targetUid  UID of the drone being tasked
     * @param cotSink    CoT endpoint to receive tasking, e.g. "192.168.1.100:8088:tcp"
     * @param taskType   Task type (default: ISR)
     */
    fun buildUavTasking(
        targetUid: String,
        callsign: String,
        cotSink: String,
        taskType: String = CotTypes.UAV_TASKING_ISR,
        lat: Double,
        lon: Double
    ): String {
        val now = System.currentTimeMillis()
        val stale = now + 60_000L
        val timeStr = isoFmt.format(Date(now))
        val staleStr = isoFmt.format(Date(stale))

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$targetUid'")
            append(" type='${CotTypes.SENSOR_SPI_LOCATION}'")
            append(" time='$timeStr'")
            append(" start='$timeStr'")
            append(" stale='$staleStr'")
            append(" how='h-e'>")  // human-entered
            append("<point lat='$lat' lon='$lon' hae='$UNKNOWN_HAE'")
            append(" ce='$UNKNOWN_CE' le='$UNKNOWN_LE'/>")
            append("<detail>")
            append("<contact callsign='$callsign'/>")
            // __services element from ServicesDetailHandler.java (ATAK-CIV)
            // Type prefix "s-" + taskType. cot:// URI is the tasking sink.
            append("<__services>")
            append("<ipfeature desc='UAV Tasking' type='s-$taskType'>")
            append("<sink mime='application/x-cot' uri='cot://$cotSink'/>")
            append("</ipfeature>")
            append("</__services>")
            append("</detail>")
            append("</event>")
        }
    }

    /**
     * Build an emergency beacon CoT event.
     * Triggers ATAK's emergency alarm and drops a distress marker.
     *
     * Sourced from EmergencyDetailHandler.java (ATAK-CIV):
     *   All b-a-* types trigger the emergency handler.
     *   Cancel by sending same UID with cancel="true" in the emergency detail.
     *
     * @param uid        Drone UID (used to cancel the same beacon later)
     * @param callsign   Who is declaring emergency
     * @param type       Emergency subtype (default "911 Alert")
     * @param cancel     Set true to cancel a previously sent emergency
     */
    fun buildEmergency(
        uid: String,
        callsign: String,
        lat: Double,
        lon: Double,
        hae: Double = UNKNOWN_HAE,
        type: String = "911 Alert",
        cancel: Boolean = false
    ): String {
        val now = System.currentTimeMillis()
        val stale = now + 300_000L  // 5 min stale for emergency
        val timeStr = isoFmt.format(Date(now))
        val staleStr = isoFmt.format(Date(stale))

        return buildString {
            append("<?xml version='1.0' encoding='UTF-8' standalone='yes'?>")
            append("<event version='2.0'")
            append(" uid='$uid'")
            append(" type='${CotTypes.EMERGENCY_911}'")
            append(" time='$timeStr'")
            append(" start='$timeStr'")
            append(" stale='$staleStr'")
            append(" how='m-g'>")
            append("<point lat='$lat' lon='$lon'")
            append(" hae='${String.format("%.2f", hae)}'")
            append(" ce='$UNKNOWN_CE' le='$UNKNOWN_LE'/>")
            append("<detail>")
            append("<contact callsign='$callsign'/>")
            append("<uid Droid='$callsign'/>")
            if (cancel) {
                append("<emergency cancel='true'/>")
            } else {
                append("<emergency type='$type'/>")
            }
            append("</detail>")
            append("</event>")
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    /** Map GPS fix quality to ATAK precisionlocation geopointsrc string */
    private fun precisionSrc(pos: GpsPosition): String = when {
        pos.fixType >= 3 && pos.hdop < 2.0 -> "GPS"
        pos.fixType >= 3 -> "GPS"
        pos.fixType == 2 -> "GPS"
        else -> "Estimated"
    }

    /** Generate a deterministic UID from callsign (stable across restarts) */
    fun uidFromCallsign(callsign: String): String =
        UUID.nameUUIDFromBytes("TAK-Bridge-$callsign".toByteArray()).toString()

    /** Sensor UID derived from drone UID */
    fun sensorUid(droneUid: String) = "$droneUid-sensor"

    /** SPI UID derived from drone UID */
    fun spiUid(droneUid: String) = "$droneUid-spi"

    /** Video connection entry UID derived from drone UID */
    fun videoUid(droneUid: String) = "$droneUid-video"
}
