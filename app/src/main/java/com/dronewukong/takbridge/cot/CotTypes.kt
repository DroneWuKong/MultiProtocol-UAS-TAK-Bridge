package com.dronewukong.takbridge.cot

/**
 * CoT type constants sourced from AndroidTacticalAssaultKit-CIV (GPLv3).
 * These are the type strings used in <event type="..."> elements.
 *
 * Format: affiliation - battle_dimension - function_id...
 *   a = atom (real-world entity)
 *   b = bits (digital/logical)
 *   t = tasking
 *   s = system
 *
 * Air affiliation prefixes:
 *   a-f = friendly
 *   a-h = hostile
 *   a-u = unknown
 *   a-n = neutral
 */
object CotTypes {

    // ─── Air Assets ────────────────────────────────────────────────────────────

    /** Friendly air (generic) */
    const val AIR_FRIENDLY = "a-f-A"

    /** Hostile air (generic) */
    const val AIR_HOSTILE = "a-h-A"

    /** Unknown air */
    const val AIR_UNKNOWN = "a-u-A"

    /** Friendly rotary-wing UAV — the primary type for FPV/multirotor drones */
    const val UAV_FRIENDLY_ROTARY = "a-f-A-M-H-Q"

    /** Friendly fixed-wing UAV */
    const val UAV_FRIENDLY_FIXED = "a-f-A-M-F-Q"

    /** Friendly ground unit (self SA) */
    const val GROUND_FRIENDLY = "a-f-G-U-C"

    // ─── Sensor / ISR ──────────────────────────────────────────────────────────

    /**
     * Sensor Point of Interest - location type.
     * Use for drone camera SPI marker. Attach <sensor> detail for FOV cone.
     * WARNING: video_spi_uid and video_sensor_uid meta values are actively
     * used by the ATAK UAS tool — do not rename these marker keys.
     */
    const val SENSOR_SPI_LOCATION = "b-m-p-s-p-loc"

    /** Sensor point of interest - general */
    const val SENSOR_SPI = "b-m-p-s-p-i"

    /** Sensor point of interest - ownship */
    const val SENSOR_SPI_OWNSHIP = "b-m-p-s-p-op"

    // ─── File Transfer ─────────────────────────────────────────────────────────

    /** File transfer request (outbound) */
    const val FILE_TRANSFER_REQUEST = "b-f-t-r"

    /** File transfer ACK (inbound confirmation) */
    const val FILE_TRANSFER_ACK = "b-f-t-a"

    // ─── Tasking ───────────────────────────────────────────────────────────────

    /** Joint message */
    const val JOINT_MESSAGE = "j-m"

    /** UAV ISR tasking (from __services detail, type field) */
    const val UAV_TASKING_ISR = "t-s-i"

    // ─── Emergency ─────────────────────────────────────────────────────────────

    /**
     * Emergency type prefix. All b-a-* types trigger the emergency handler.
     * Full type is b-a-<emergency_type>, e.g. b-a-o-opn (alert/open)
     */
    const val EMERGENCY_PREFIX = "b-a"

    /** Standard 911 / distress beacon */
    const val EMERGENCY_911 = "b-a-o-opn"

    // ─── Misc ──────────────────────────────────────────────────────────────────

    /** Checkpoint / waypoint */
    const val CHECKPOINT = "b-m-p-c-cp"

    /** IP address contact point */
    const val CONTACT_IP = "b-m-p-c-ip"

    // ─── Broadcast ports (from commoncommo atakcotcaptures.txt) ────────────────

    /** Default SA/non-chat multicast port */
    const val UDP_PORT_SA = 6969

    /** Chat multicast port */
    const val UDP_PORT_CHAT = 17012

    /** Default multicast group */
    const val MULTICAST_GROUP = "239.2.3.1"

    /** TAK Server default CoT TCP port */
    const val TAK_SERVER_TCP_PORT = 8087

    /** TAK Server default TLS port */
    const val TAK_SERVER_TLS_PORT = 8089

    /** TAK Server mission package http port */
    const val TAK_SERVER_HTTP_PORT = 8080

    /** TAK Server mission package https port */
    const val TAK_SERVER_HTTPS_PORT = 8443
}
