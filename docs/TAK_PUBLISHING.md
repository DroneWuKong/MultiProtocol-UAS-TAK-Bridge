# TAK position and video-link publishing

The public bridge publishes documented telemetry through a source-neutral `TakPublication` boundary. MAVLink, MSP and GHST/CRSF still feed the existing GPS model. No additional aircraft commands, proprietary accessory decoder or binary-analysis tools are included in this change.

## Sharing an existing video feed

1. Stop the bridge, open **Connection settings → TAK video link**.
2. Enter an RTSP address reachable from the receiving devices, enable sharing, and save.
3. Start the existing telemetry connection. Fresh valid aircraft positions now carry the video address in the same CoT event.
4. On a receiving ATAK device, verify the aircraft marker and open its video link. The camera/relay must already be serving video, and network routing and viewer credentials must be configured separately.

This shares an address, not video bytes. It neither creates an RTSP server nor starts the camera. The full address, including query parameters, goes to every recipient of the marker. Usernames/passwords in URL userinfo, fragments, malformed addresses and common loopback/unspecified addresses are rejected. Do not put access tokens in the query or path. Host validation does not prove reachability or perform DNS resolution. The saved address is app-private, omitted from diagnostic exports and cleared when sharing is disabled.

To remove a shared link, stop the bridge, disable sharing and save, then restart with valid GPS. The next position contains an explicit empty video URL with the same video UID. A stopped/disconnected source cannot immediately retract a previously received marker or link; receiving clients may retain it until updated or removed. Verify behavior on the deployed ATAK version.

**Software demo** previews this same event locally with a simulation identity and never opens network output. Settings cannot change while a session is running. Unsaved edits to the video settings are discarded when the screen is recreated.

## Publication behavior

- Only fresh, valid GPS produces a position event. Unknown altitude reference is not relabeled as HAE: the point uses the existing unknown-HAE sentinel and preserves MSL in remarks.
- Position and video metadata share one event, one aircraft UID and one real coordinate. There is no independent video event at an invented `0,0` position. Legitimate coordinates at `0,0` remain valid.
- Fresh installations persist a UUID-based aircraft identity; changing the callsign preserves it. Existing stored IDs are preserved. Two installations with an old duplicated callsign-derived ID still need distinct IDs configured/migrated by the operator.
- Each queued publication expires at the earlier of GPS freshness expiry (five seconds) and its CoT stale timestamp. Reconnect does not flush expired positions.
- Text fields are XML escaped and reject invalid XML characters. Numbers use dot decimals; timestamps use an immutable UTC formatter. Invalid/infinite accuracy is represented as unknown.
- The legacy video-alias helper now requires a fresh GPS position. Explicit-point helpers also reject invalid coordinates; they remain unwired to live sensor/tasking output. No camera footprint or ground target is guessed from aircraft GPS alone.

## What delivery evidence means

The main display says **Socket writes**. Diagnostics retain per-output write counts and add last-write timestamps, expired-event counts, video-sharing state, `counter_scope=PROCESS_LIFETIME`, `delivery_evidence=SOCKET_WRITES_ONLY` and `atak_receipt=UNCONFIRMED`.

A connected TCP/TLS socket and successful writes do not prove server acceptance, routing, ATAK display or video playback. There is no application-level receipt protocol in this implementation. UDP and TCP counters count transport writes separately, even when both carry the same event. Counters and last-write timestamps persist for the app process and can describe a previous session.

## Sources and provenance scope

The new publication, validation, settings and test code is authored in this repository using the existing public bridge model and publicly visible CoT wire conventions. No vendor APK, disassembly, camera-control schema or vendor resource was imported. No new runtime dependency was added.

The inherited formatter already cited these public ATAK sources:

- [VideoDetailHandler](https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV/blob/master/atak/ATAK/app/src/main/java/com/atakmap/android/video/cot/VideoDetailHandler.java): `__video` attributes and empty-URL removal behavior.
- [ATAK-CIV repository](https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV): GPL-licensed upstream reference; existing attribution remains intact.

The implementation adds/removes wire attributes; it does not embed the upstream handler or its classes. This scoped provenance record does not certify all historical source origins or grant permission to relicense upstream code. The repository lacks a root project license; an appropriate rights/license review remains necessary before declaring the whole project permissively licensed. Existing bundled dependency licenses remain in their vendor directories. Keeping a dependency or wire reference public does not erase its license obligations.

## Verification and limits

Tests cover parsed CoT XML, real coordinates, unknown HAE, link removal, URL rejection, expiry boundaries, concurrent timestamps, simulation isolation, settings persistence, stable IDs, redacted diagnostics and an actual loopback TCP receiver. Existing TCP reconnect, mutual TLS, certificate rejection, telemetry and UI suites remain required.

Run `./gradlew testDebugUnitTest assembleDebug lintDebug --no-daemon --max-workers=1` with JDK 17, then `python3 tools/verify_build.py`. Generated reports are under `app/build/reports/`; `ui/tak-video-settings.png` is a Robolectric UI rendering, not physical device evidence.

A loopback peer is not a TAK Server or ATAK client. Physical telemetry input, deployed server routing, marker/video association and playback still require a receiving-device bench test. No test here establishes legal clearance or independent qualification.

## Local validation, 2026-11-25

GupB / Linux, JDK 17, Android SDK 35: 56 native tests passed with no failures, errors or skips. Debug assembly, lint and `tools/verify_build.py` passed. Lint reported 114 warnings and 0 errors; the inherited AGP 8.2.2 warns about compileSdk 35. Browser checks passed: 13 panels, 39 navigation checks across three viewport widths, 20 functional checks and no script errors. The phone-size settings rendering was visually inspected.

Local debug APK: 7,722,635 bytes; SHA-256 `9432b1f2ce6743ebb5d3a3d785350fdf3f998747d2e791fc1de2d20cb87568bf`. Build environments and debug keys can change APK bytes. These results cover software validation, not physical ATAK receipt or playback.
