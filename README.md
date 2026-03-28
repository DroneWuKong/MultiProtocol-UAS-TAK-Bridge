# TAK Bridge

**Drone GPS → MGRS → TAK**

Standalone Android app that reads GPS telemetry from a drone flight controller over USB, converts coordinates to MGRS, and pushes Cursor on Target (CoT) events to TAK (ATAK/WinTAK/iTAK).

Part of the [AI Wingman](https://github.com/DroneWuKong/Ai-Project) ecosystem, but runs independently with zero dependencies on other Wingman components.

## What It Does

```
Drone FC ──USB Serial──→ MAVLink GPS Parser ──→ MGRS Converter ──→ CoT Formatter ──→ TAK
                                                      │                                    ├─ Multicast UDP (239.2.3.1:6969)
                                                      │                                    └─ TAK Server TCP
                                                      └──→ Live MGRS Display
```

1. **Connects** to a flight controller via USB OTG (S25 → FC)
2. **Parses** MAVLink v2 GPS messages (GPS_RAW_INT, GLOBAL_POSITION_INT)
3. **Converts** lat/lon to MGRS using NGA's official library
4. **Displays** MGRS grid coordinate, lat/lon, fix quality, satellites, altitude, speed
5. **Pushes** CoT events to ATAK/WinTAK via multicast (zero config) or TAK Server (TCP)

## Supported Hardware

- **Flight Controllers:** Any FC with MAVLink v2 output (PX4, ArduPilot)
- **USB Chips:** CP2102, FTDI, STM32 CDC, CH340
- **Ground Station:** Samsung Galaxy S25 (primary target), any Android 8.0+ with USB OTG

## TAK Integration

### Multicast (No Server)
Enabled by default. Broadcasts CoT events on `239.2.3.1:6969`. Any ATAK/WinTAK/iTAK device on the same network sees the drone appear as a friendly UAV marker.

### TAK Server (TCP)
Enter your TAK Server IP and port (default 8087). The app maintains a persistent TCP connection and pushes CoT events at 1Hz.

### CoT Details
- **Type:** `a-f-A-M-H-Q` (Friendly, Air, Military, Rotary-wing, UAV)
- **UID:** Persistent per callsign
- **Stale:** 30 seconds (configurable)
- **CE90:** Estimated from HDOP
- **Detail:** Callsign, speed, heading, fix quality, satellite count

## Architecture

```
com.dronewukong.takbridge/
├── mavlink/
│   ├── GpsPosition.kt          # Source-agnostic GPS data class
│   └── MavlinkGpsParser.kt     # Minimal MAVLink v2 GPS extraction
├── mgrs/
│   └── MgrsConverter.kt        # NGA MGRS library wrapper
├── cot/
│   ├── CotTypes.kt             # MIL-STD-2525 type codes
│   └── CotFormatter.kt         # CoT XML event builder
├── transport/
│   ├── UsbSerialTransport.kt   # USB serial connection manager
│   ├── TakSender.kt            # Multicast + TCP output
│   └── TakConfig.kt            # TAK output configuration
└── ui/
    └── MainActivity.kt         # Single-screen bridge UI
```

## Building

Standard Android Studio project. Open the `tak-bridge/` directory, sync Gradle, build.

```bash
./gradlew assembleDebug
```

## Dependencies

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) — USB serial driver
- [NGA MGRS](https://ngageoint.github.io/mgrs-android/) — Official MGRS coordinate library
- AndroidX / Material Components / Kotlin Coroutines

## What This Is NOT

This is not a flight controller. This is not a GCS. This is not ATAK. This is a **bridge** — it reads position data and relays it to TAK. It does not command the drone, change flight modes, or modify any parameters. It just watches and reports.

## Visual Identity

Dark theme. Teal (`#4ECDC4`) accent. Monospace. Like the rest of the AI Wingman family.

---

*Buddy up.*
