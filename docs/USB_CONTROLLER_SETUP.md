# USB controller → Android → TAK

## First setup to try

1. On USB controller, select **USB port → MAVLink transcode**. The last recorded tested
   controller firmware in Operator was v1.3.8 alpha; no firmware update is
   performed by this app. Use firmware that already exposes this mode.
2. Connect USB controller's **USB-C data port** to the Android phone/tablet with a data
   cable and USB host/OTG support. The controller RJ45 connector is not the phone connection.
3. In TAK Bridge select **USB controller · MAVLink transcode**. This selects MAVLink,
   115200 and DTR. Press **CONNECT**, choose the serial port if prompted, and
   approve Android's USB access request. A hub with multiple serial devices
   always shows a picker; the app does not choose a random radio.
4. With the normal aircraft telemetry link established, watch the byte/frame
   counters and GPS fix display. MAVLink 1 and 2 are accepted. GPS_RAW_INT gives
   fix evidence; GLOBAL_POSITION_INT can update position while that evidence is
   fresh. Heartbeats alone do not imply GPS or refresh old coordinates.
5. Use the existing TAK multicast or server settings. CoT publishing starts only
   with valid fresh coordinates. USB loss, no-fix and GPS silence stop publishing.

This is the preferred compatibility attempt, not a claim that controller transcode GPS
has been measured on hardware. The app receives; it does not request MAVLink
streams or send heartbeats to the aircraft. A silent upstream must be configured
at its source.

## GHST alternate

Stop the connection. Select **GHST C2/Telemetry** on USB controller and
**USB controller · GHST C2/Telemetry** in the app, then reconnect. DTR remains enabled.
GHST uses GPS types `0x25`/`0x26`; CRSF uses GPS type `0x02` with its separate
endianness and units. CRC is checked before either decoder publishes a position.

Operator physically captured 2,150 CRC-valid controller USB RC frames at the requested
115200 line setting on September 4, 2026 (VID:PID `35B6:0004`). Two captures
without DTR were silent; asserting DTR enabled the stream. That capture contained
RC pages only. It did not prove aircraft GPS through controller USB.

| App status | Meaning / next check |
|---|---|
| No USB serial interface | Check data cable, Android USB host role and controller USB mode; HID gamepad mode alone is not telemetry. |
| Permission denied | Press CONNECT again and approve Android USB access. |
| USB open, no bytes | Check controller mode and DTR; 115200 is the observed line setting, not a universal UART rule. |
| Controller channels only; no GPS | controller is streaming valid stick frames. Check upstream telemetry and try MAVLink transcode; stick movement cannot provide a GPS fix. |
| Bytes but zero decoded frames | Check selected protocol/port/baud. CRC failures or unsupported packets are not position data. |
| GPS frames but no fix | Check fix status at the source. GHST fix flags are respected; the rig-specific Operator override is not enabled. |
| GPS STALE | More than five seconds without a fresh position; CoT transmission has stopped. |

Stop before changing a profile, protocol, baud or DTR. Selections persist. For a
flight controller connected directly by USB, choose **Direct FC · MSP GPS
polling** to send only the MSP_RAW_GPS request. Passive/controller profiles never poll
MSP, including when their protocol dropdown is changed to MSP.

## What was reused and what remains to test

Ported behavior is grounded in Operator's CRC framing, GHST field interpretation,
fix flags, independent freshness and reconnect rules. The Android transport adds
permission handling, explicit serial-port selection, controller CDC discovery and DTR.
MAVLink packet fixtures come from pymavlink, not this decoder.

Remaining physical acceptance: confirm Android enumeration/permission and DTR on
the actual controller, receive aircraft GPS in one USB mode, compare position/fix with
the source, view the marker in ATAK, then unplug/reconnect and verify no old
position resumes. No APK build or software fixture replaces that check.

Aircraft telemetry does not use the phone location permission. The existing Tools
phone-GPS helper checks optional Android location permission and returns an error
when unavailable; phone location is never substituted for aircraft telemetry.

MSL altitude is retained in the display/CoT remarks. HAE stays unknown until a
geoid correction or genuine HAE source exists. MAVLink signatures are consumed
for framing but are not authenticated. A MAVLink connection pins its first GPS
system/component until reconnect, preventing data from separate sources being
combined under one marker.

Sources:
- https://github.com/DroneWuKong/Prismo-Operator/blob/2a569fbe0c03b31e7baafbcebccbeb63950ab3d5/docs/OMG_TAC_OBSERVER_INSTALL_2026-09-01.md
- https://github.com/DroneWuKong/Prismo-Operator/blob/2a569fbe0c03b31e7baafbcebccbeb63950ab3d5/gateway/ghst_telemetry.py
- https://mavlink.io/en/guide/serialization.html
