# Connecting an existing USB telemetry stream

This guide covers the bridge after the source already provides supported
telemetry. Device-side feature discovery, activation menus and controller-specific
enablement are outside its scope.

## In the bridge

1. Connect a compatible USB aircraft telemetry output to an Android device with
   a data cable and USB host/OTG support.
2. Choose the app profile, protocol and stream settings that match the existing
   source. Press **CONNECT**, select the serial port when prompted, and approve
   Android USB access. Multiple serial devices always show a picker.
3. Watch the decoded frame counters, GPS fix and freshness. MAVLink heartbeats
   and controller stick frames alone do not imply aircraft GPS.
4. Configure TAK output and verify the marker in the receiving client. Publishing
   requires valid fresh aircraft coordinates. USB loss, no-fix and GPS silence
   stop publication.

The bridge does not request MAVLink streams or send aircraft heartbeats.
GHST GPS types `0x25`/`0x26` and CRSF GPS type `0x02` have distinct field formats;
CRC is checked before either decoder publishes a position.

| App status | Meaning / next check |
|---|---|
| No USB serial interface | Check data cable, Android USB host support and whether a compatible serial source is present. |
| Permission denied | Press CONNECT again and approve Android USB access. |
| USB open, no bytes | Verify an existing telemetry stream and matching app port/settings. |
| Controller channels only; no GPS | Stick frames are arriving; they cannot provide aircraft GPS. |
| Bytes but zero decoded frames | Check the stream format and app protocol/port/baud. CRC failures or unsupported packets are not position data. |
| GPS frames but no fix | Check fix status at the source. Source fix flags are respected. |
| GPS STALE | More than five seconds without a fresh position; CoT transmission has stopped. |

Stop before changing app stream settings. Selections persist. The explicit
**Direct FC · MSP GPS polling** profile sends only the MSP_RAW_GPS request.
Passive profiles never poll MSP, including when their protocol dropdown changes
to MSP.

## What was reused and what remains to test

Ported behavior is grounded in Operator's CRC framing, GHST field interpretation,
fix flags, independent freshness and reconnect rules. The Android transport adds
permission handling, explicit serial-port selection, controller CDC discovery and DTR.
MAVLink packet fixtures come from pymavlink, not this decoder.

Remaining physical acceptance: confirm Android enumeration/permission and serial
control-line behavior with the actual source, receive aircraft GPS, compare position/fix with
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
- https://github.com/DroneWuKong/Prismo-Operator/blob/2a569fbe0c03b31e7baafbcebccbeb63950ab3d5/gateway/ghst_telemetry.py
- https://mavlink.io/en/guide/serialization.html
