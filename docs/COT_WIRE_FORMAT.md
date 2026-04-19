# CoT Wire Format Reference
## Sourced from AndroidTacticalAssaultKit-CIV (GPLv3)

This document is the authoritative CoT reference for all Wingman products.
Source: `takproto/`, `takcot/xsd/`, `commoncommo/core/` from ATAK-CIV.

---

## Wire Formats

### UDP Multicast (Zero-Config SA)
- Group: `239.2.3.1`
- Port: `6969` (SA/non-chat), `17012` (chat)
- Payload: Raw UTF-8 XML CoT event. No framing, no protobuf.
- Any ATAK device on the same network sees the marker instantly.

### TAK Server TCP / TLS
- Default ports: `8087` (TCP), `8089` (TLS)
- Payload: `[0xBF][varint length][TakMessage protobuf]`
- TakMessage wraps either TakControl (version negotiation) or CotEvent (protobuf).
- For version 1 (assumed if minProtoVersion/maxProtoVersion both 0), send raw XML inside TakMessage.

### Direct P2P TCP
- Port: 4242 (conventional, configurable)
- Payload: Same framing as TAK Server

---

## CoT Event Structure

```xml
<?xml version='1.0' encoding='UTF-8' standalone='yes'?>
<event version="2.0"
       uid="PERSISTENT-UUID"
       type="a-f-A-M-H-Q"
       time="2026-01-01T00:00:00.000Z"   <!-- when sent -->
       start="2026-01-01T00:00:00.000Z"  <!-- valid from -->
       stale="2026-01-01T00:00:30.000Z"  <!-- expire after (30s typical) -->
       how="m-g">                         <!-- machine-generated -->

  <!-- WGS-84 position. hae = Height Above Ellipsoid (meters).
       ce = Circular Error (meters radius). le = Linear Error (meters height).
       Use 9999999 for unknown values. -->
  <point lat="41.8781" lon="-87.6298" hae="200.0" ce="5.0" le="9999999"/>

  <detail>
    <!-- callsign shown in ATAK contact list. endpoint for direct routing -->
    <contact callsign="DRONE-01" endpoint="192.168.1.100:4242:tcp"/>

    <!-- Droid = display name in ATAK -->
    <uid Droid="DRONE-01"/>

    <!-- team color and role -->
    <__group name="Cyan" role="Team Member"/>

    <!-- course = degrees true north, speed = m/s -->
    <track course="045.0" speed="12.5"/>

    <!-- GPS fix source. geopointsrc: GPS|DGPS|User|Estimated|Simulated -->
    <!-- altsrc: GPS|DTED0|DTED1|DTED2|DTED3|LIDAR|User -->
    <precisionlocation geopointsrc="GPS" altsrc="GPS"/>

    <!-- battery 0-100 -->
    <status battery="85"/>

    <!-- sender identification -->
    <takv device="Orqa APB" platform="TAK Bridge" os="Android" version="1.0"/>
  </detail>
</event>
```

---

## CoT Types for UAS Operations

| Type | Description | ATAK Icon |
|------|-------------|-----------|
| `a-f-A-M-H-Q` | Friendly rotary UAV | Blue rotary UAV |
| `a-f-A-M-F-Q` | Friendly fixed-wing UAV | Blue fixed UAV |
| `a-f-A` | Friendly air (generic) | Blue air symbol |
| `a-h-A` | Hostile air | Red air symbol |
| `a-u-A` | Unknown air | Yellow air symbol |
| `b-m-p-s-p-loc` | Sensor SPI (camera target) | Crosshair |
| `b-m-p-s-p-i` | Sensor point of interest | Sensor icon |
| `b-a-o-opn` | Emergency / 911 | Red alert |

---

## Sensor FOV Detail Element

Draws a camera field-of-view cone on the ATAK map.

```xml
<sensor
  azimuth="090"         <!-- Camera heading, TRUE NORTH degrees 0-360 -->
  fov="90"              <!-- Horizontal FOV, EDGE-TO-EDGE (NOT half-angle!) -->
  vfov="60"             <!-- Vertical FOV in degrees -->
  range="500"           <!-- Max range of cone in meters (hard max: 15000) -->
  roll="0"              <!-- Camera roll in degrees -->
  elevation="0"         <!-- Depression angle (negative=down) -->
  fovAlpha="0.3"        <!-- Cone fill transparency (0=invisible, 1=opaque) -->
  fovRed="0.0" fovGreen="0.8" fovBlue="1.0"   <!-- Cone color (RGB 0.0-1.0) -->
  strokeColor="-1"      <!-- Outline color (ARGB int, -1 = white) -->
  strokeWeight="1.0"    <!-- Outline thickness -->
  displayMagneticReference="0"   <!-- 0=true north, 1=magnetic north -->
/>
```

**CRITICAL**: `fov` is edge-to-edge, NOT center-to-edge. A 90° camera lens has `fov="90"`.

---

## Video Detail Element

Attaches an RTSP/RTMP stream to a drone marker.

```xml
<__video
  url="rtsp://192.168.1.100:8554/live"
  uid="video-connection-entry-uid"
  spi="drone-uid-spi"      <!-- Links video to SPI marker (camera target point) -->
  sensor="drone-uid-sensor" <!-- Links video to sensor FOV cone marker -->
  buffer="-1"              <!-- Buffer in ms, -1 = auto -->
  timeout="5000"           <!-- Connection timeout in ms -->
/>
```

**WARNING** (from ATAK-CIV VideoDetailHandler.java):
`video_spi_uid` and `video_sensor_uid` marker meta keys are used by the ATAK UAS tool.
Do not rename or repurpose these keys in any Wingman ATAK plugin.

---

## UAV Tasking via __services

How ATAK operators push ISR tasking to a drone.

```xml
<__services>
  <ipfeature desc="UAV Tasking" type="s-t-s-i">
    <sink mime="application/x-cot" uri="cot://192.168.1.100:8088;tcp"/>
  </ipfeature>
</__services>
```

Wingman Command should listen for `type` attribute starting with `s-` and
route the task to the autonomy layer via the CoT endpoint in the `uri`.

---

## Emergency Detail Element

```xml
<!-- Declare emergency -->
<emergency type="911 Alert"/>

<!-- Cancel emergency (same UID as the declaring event) -->
<emergency cancel="true"/>
```

All CoT events with type starting `b-a` trigger ATAK's emergency handler.
EmergencyDetailHandler.java (ATAK-CIV): fires red alert, drops distress marker.
**Wingman response**: trigger RTH or loiter + operator notification.

---

## Contact Endpoint Routing

From `commoncommo/core/contact-rxtx-rules.txt` (ATAK-CIV).

Priority order when sending to a contact:
1. TCP direct endpoint if ever seen
2. UDP endpoint if seen more recently than TCP
3. TAK Server streaming if local endpoints stale > 60 seconds

Endpoint string formats:
- `"192.168.1.100:4242:tcp"` — direct TCP
- `"192.168.1.100:4242:udp"` — direct UDP (unreliable)
- `"*:-1:stcp"` — via TAK Server (streaming TCP)
- `"192.168.1.100:4242:srctcp"` — reply to sender's source IP

UDP discovery: when ATAK receives a multicast packet, it reads the `endpoint` 
attribute from `<contact>` to discover the sender's TCP address. If type is `tcp`, 
it will send subsequent direct messages there instead of multicast.

---

## Broadcast Ports

| Port | Protocol | Purpose |
|------|----------|---------|
| 6969 | UDP | SA (situational awareness) — all non-chat CoT |
| 17012 | UDP | Chat CoT messages |
| 4242 | TCP | Direct device-to-device CoT |
| 8087 | TCP | TAK Server CoT |
| 8089 | TLS | TAK Server CoT (encrypted) |
| 8080 | HTTP | TAK Server mission package transfer |
| 8443 | HTTPS | TAK Server mission package transfer (secure) |

---

## Protocol Version Negotiation

From `takproto/takcontrol.proto` (ATAK-CIV):

```protobuf
message TakControl {
  uint32 minProtoVersion = 1;  // If 0, assume version 1
  uint32 maxProtoVersion = 2;  // If 0, assume version 1
  string contactUid = 3;       // Optional if paired with CotEvent
}
```

Send a `TakMessage` containing only `TakControl` on connect to negotiate protocol version.
If the remote sends back a `TakControl` with a range that includes your version, proceed.

---

## Protobuf Schema Summary

Package: `atakmap.commoncommo.protobuf.v1`

```
TakMessage
  ├── TakControl (optional — version negotiation)
  │     ├── minProtoVersion: uint32
  │     ├── maxProtoVersion: uint32
  │     └── contactUid: string
  └── CotEvent (optional — the actual event)
        ├── type, uid, how: string
        ├── sendTime, startTime, staleTime: uint64 (ms since epoch)
        ├── lat, lon, hae, ce, le: double
        └── Detail
              ├── xmlDetail: string (raw XML for non-typed fields)
              ├── Contact { endpoint, callsign }
              ├── Group { name, role }
              ├── PrecisionLocation { geopointsrc, altsrc }
              ├── Status { battery: uint32 }
              ├── Takv { device, platform, os, version }
              └── Track { speed, course: double }
```
