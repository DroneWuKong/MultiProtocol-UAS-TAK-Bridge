package com.dronewukong.takbridge.cot

/**
 * CoT (Cursor on Target) type strings for TAK.
 *
 * Format: a-{affiliation}-{battle dimension}-{function}
 * Affiliation: f=friendly, h=hostile, u=unknown, n=neutral
 * Battle dimension: A=air, G=ground, S=sea
 *
 * Reference: MIL-STD-2525 / CoT Event Schema
 */
object CotTypes {
    // Friendly air — UAS
    const val FRIENDLY_UAV = "a-f-A-M-F-Q"          // Friendly, Air, Military, Fixed-wing, UAV
    const val FRIENDLY_UAV_ROTARY = "a-f-A-M-H-Q"   // Friendly, Air, Military, Rotary-wing, UAV

    // Generic markers
    const val FRIENDLY_GROUND = "a-f-G"               // Friendly ground
    const val UNKNOWN_AIR = "a-u-A"                    // Unknown air

    // Atoms (generic point of interest)
    const val SENSOR_POINT = "b-m-p-s-p-i"            // Sensor point

    // Default for our drone
    const val DEFAULT = FRIENDLY_UAV_ROTARY
}
