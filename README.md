# MultiProtocol UAS TAK Bridge

**Drone GPS → MGRS → TAK**

Standalone Android app that reads GPS telemetry from any drone flight controller over USB — MAVLink, MSP, or GHST — converts coordinates to MGRS, and pushes Cursor on Target (CoT) events to TAK (ATAK/WinTAK/iTAK) in real time.

Part of the [AI Wingman](https://github.com/DroneWuKong/Ai-Project) ecosystem, but runs independently with zero dependencies on other Wingman components.

## What It Does

```
Drone FC ──USB OTG──→ Protocol Auto-Detect ──→ MGRS Converter ──→ CoT Formatter ──→ TAK
                       ├─ MAVLink v2                                                  ├─ Multicast UDP (239.2.3.1:6969)
                       ├─ MSP v1/v2              Live Map with                        ├─ TAK Server TCP
                       └─ GHST/CRSF              Drone Marker + Trail                 └─ TAK Server TLS (.p12 cert)
```

1. **Connects** to a flight controller via USB OTG (phone → FC or phone → transmitter)
2. **Auto-detects** protocol: MAVLink v2, MSP v1/v2, or GHST/CRSF
3. **Parses** GPS from any firmware: PX4, ArduPilot, Betaflight, iNav
4. **Converts** lat/lon to MGRS using NGA's official library
5. **Displays** live map with drone marker (heading rotation), breadcrumb trail, MGRS grid coordinate, fix quality, satellites, altitude, speed
6. **Pushes** CoT events to ATAK/WinTAK/iTAK via multicast (zero config), TAK Server TCP, or TAK Server TLS

## Supported Protocols

| Protocol | Firmware | GPS Behavior | Default Baud |
|----------|----------|--------------|--------------|
| MAVLink v2 | PX4, ArduPilot | Auto-streams GPS | 115200 |
| MSP v1/v2 | Betaflight, iNav | Polled at 2Hz by app | 115200 |
| GHST/CRSF | Via IRONghost radio | Native GPS frames + MSP passthrough | 115200 |

Auto-detect feeds all three parsers simultaneously — first valid GPS fix locks the protocol. Manual override available via protocol spinner.

## Supported Hardware

- **Flight Controllers:** Any FC running PX4, ArduPilot, Betaflight, or iNav with a GPS module
- **USB Chips:** CP2102, FTDI, STM32 CDC, CH340
- **Radio Link:** IRONghost JR module via Jumper T20S (GHST/CRSF telemetry mirror over USB)
- **Ground Station:** Samsung Galaxy S25 (primary target), any Android 8.0+ with USB OTG
- **Map Tiles:** OpenStreetMap via OSMDroid (no API key required, works offline with cached tiles)

## Connection Methods

### Direct to Flight Controller (Recommended for demos)
Phone USB-C → OTG adapter → FC USB port. Simplest path — no radio config needed. FC must have GPS module with satellite fix.

### Through Transmitter (For live flight telemetry)
Phone USB-C → OTG adapter → Transmitter USB port. Requires EdgeTX USB serial mode set to VCP/Debug with telemetry mirroring enabled on the external module.

> **Note:** The Orqa TAC controller is a USB host — direct USB connection won't work. Use FC-direct or a radio with USB serial telemetry output.

## TAK Integration

### Multicast (No Server)
Enabled by default. Broadcasts CoT events on `239.2.3.1:6969`. Any ATAK/WinTAK/iTAK device on the same Wi-Fi sees the drone appear as a friendly UAV marker. Zero configuration.

### TAK Server (TCP)
Enter your TAK Server IP and port (default 8087). Auto-reconnect with linear backoff on disconnect.

### TAK Server (TLS)
Check the TLS box, load your `.p12` client certificate, port 8089. Mutual TLS with PKCS12 client cert — standard TAK Server auth.

### CoT Details
- **Type:** `a-f-A-M-H-Q` (Friendly, Air, Military, Rotary-wing, UAV)
- **UID:** Persistent per callsign
- **Update Rate:** 1Hz
- **Stale:** 30 seconds
- **CE90:** Estimated from HDOP
- **Detail:** Callsign, speed, heading, fix quality, satellite count

## Map Features

- **Dark/tactical map** — inverted OpenStreetMap tiles
- **Drone marker** — teal chevron that rotates with heading
- **Breadcrumb trail** — teal line showing flight path (500 point history, 2m minimum spacing)
- **Auto-follow** — map tracks drone position, tap map to pan freely, tap ◎ to re-center
- **MGRS overlay** — large monospace grid coordinate displayed over map

## Architecture

```
com.dronewukong.takbridge/
├── mavlink/
│   ├── GpsPosition.kt          # Source-agnostic GPS data class
│   ├── MavlinkGpsParser.kt     # MAVLink v2 GPS extraction
│   ├── MspGpsParser.kt         # MSP v1/v2 GPS extraction (Betaflight/iNav)
│   ├── GhstPassthrough.kt      # GHST/CRSF frame parser + MSP passthrough
│   └── ProtocolRouter.kt       # Auto-detect + route to correct parser
├── mgrs/
│   └── MgrsConverter.kt        # NGA MGRS library wrapper
├── cot/
│   ├── CotTypes.kt             # MIL-STD-2525 type codes
│   └── CotFormatter.kt         # CoT XML event builder
├── transport/
│   ├── UsbSerialTransport.kt   # USB serial connection manager
│   ├── TakSender.kt            # Multicast + TCP + TLS output
│   ├── TakConfig.kt            # TAK output configuration
│   └── ConfigStore.kt          # SharedPreferences persistence
└── ui/
    └── MainActivity.kt         # Map-first single-screen UI
```

## Building

Standard Android Studio project. Clone, open, sync Gradle, build.

```bash
gradle wrapper --gradle-version 8.5
./gradlew assembleDebug
```

## Dependencies

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) — USB serial driver
- [NGA MGRS](https://ngageoint.github.io/mgrs-android/) — Official MGRS coordinate library (mil.nga.mgrs)
- [OSMDroid](https://github.com/osmdroid/osmdroid) — OpenStreetMap tiles (no API key)
- AndroidX / Material Components / Kotlin Coroutines

## Quick Start Guide

See [`docs/TAK_Bridge_Quick_Start.pdf`](docs/TAK_Bridge_Quick_Start.pdf) for a printable 2-page guide covering setup, connection methods, TAK output, and troubleshooting.

## What This Is NOT

This is not a flight controller. This is not a GCS. This is not ATAK. This is a **bridge** — it reads position data and relays it to TAK. It does not command the drone, change flight modes, or modify any parameters. It just watches and reports.

## Visual Identity

Dark theme. Teal (`#4ECDC4`) accent. Monospace. Like the rest of the AI Wingman family.

---

*Buddy up.*
