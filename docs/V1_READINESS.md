# TAK Bridge v1 readiness

Current implementation: 0.4.0 development build, October 2, 2026.

The Prismo UI refresh adds scrollable connection details and illustrated public
guides. It retains the telemetry and transport implementation described below.
Native layout checks cover phone portrait, landscape and tablet dimensions;
software renders do not replace the remaining physical acceptance checks.

## What was fixed

The 0.2.3 branch's Unix `gradlew` was an empty executable. CI's Gradle step
returned success without tests or compilation, then the APK hash step failed.
This was separate from the earlier, real local test/build evidence. The wrapper
is restored from Gradle 8.5, with the existing distribution checksum retained.
CI now verifies the launcher version, runs a clean build, and requires nonempty
passing native test suites, an intact APK with expected assets, and a lint report.

Past documentation checked before changing behavior:
`SOFTWARE_USB_CONTROLLER_2026-09-29.md`, `USB_CONTROLLER_SETUP.md`,
`UI_REPAIR_2026-09-29.md`, and `TOOLS_AUDIT_2026-09-29.md`.
The fragment startup repair and Tools fixes are retained. The former activity-owned
USB/sender lifecycle and ignored certificate password were independent defects.

`BridgeApplication` holds one application-scoped `BridgeSession`. The activity
observes it only while visible. `BridgeService` owns the active session and a
connected-device foreground notification with Stop. A bounded, renewed wake lock
supports screen-off operation and is released on stop. Process death does not
silently restore a connection: `START_NOT_STICKY` requires an explicit restart.
USB removal/read failure/permission denial clear the position and end the session;
reconnect with CONNECT. Automatic selection of a replacement USB radio is not used.

TCP has one writer and one EOF reader per connection generation. Stop closes
connecting and connected sockets. A bounded latest-event queue discards events
past their source-freshness deadline. Reconnection backoff tops out at 15 seconds.
UDP sends from an ephemeral source port; ATAK can bind its receive port locally.

Client PKCS12 imports validate the private key and certificate. Passwords are
session-only, not saved in preferences or Android view state. CA trust can come
from a separate imported CA, CA certificates in the client chain, or system roots.
Server hostname verification is mandatory. Clear certificates from the certificate
menu to remove app-private files and the in-memory password.

## Software-only path and hardware boundaries

| Boundary | Production entry point | Software path |
|---|---|---|
| Android USB discovery, permission, DTR, serial read | `UsbSerialTransport`, `BridgeSession.startUsb` | `BridgeSession.startReplay` uses bundled MAVLink bytes through the same `ProtocolRouter` |
| Fresh GPS to CoT | `BridgeSession.publish`, `CotFormatter.buildDroneSA` | Same validity/freshness checks and formatter; demo retains XML locally with SIMULATION identity |
| Actual network output | `TakSender.send` | Loopback TCP/mTLS test servers; no external TAK server required |
| Actual Android service scheduling and power behavior | `BridgeService` | Robolectric lifecycle and accelerated-clock replay; physical power behavior remains a device check |

The demo loops ten GPS frames, seven seconds of silence, and three no-fix frames.
Use DIAGNOSTICS → CoT preview to inspect the last event. A stale preview is labeled
with current GPS state; its timestamp is unchanged. The demo cannot enable real
TAK output. Test fixtures are synthetic/recorded-format packets, not evidence of
controller aircraft GPS. No phone-position substitution or flight-control commands exist.
The explicit Direct FC MSP profile remains the only serial write path.

## Reproduce checks

Prerequisites: full JDK 17, Android SDK 35/build tools, Node 22, Python 3.

```sh
./gradlew clean testDebugUnitTest assembleDebug lintDebug --no-daemon
python3 tools/verify_build.py
npm ci --prefix tools/ui-tests
cd tools/ui-tests
npx playwright install --with-deps chromium
npm test
```

The native tests include activity teardown while the service runs, notification
Stop, null-intent restart behavior, fresh/stale/no-fix replay, an accelerated
two-hour simulation and twenty restarts, diagnostic redaction, plaintext-password
migration, local TCP delivery/EOF/reconnect/expiration, connection-generation
cleanup, password-protected mutual TLS, untrusted CA and hostname rejection.
Test certificates are generated with the JDK's keytool and deleted afterward.
No hardware, private production credentials, or external TAK server are needed.

## Software validation performed September 30

- Clean Gradle wrapper build: `testDebugUnitTest assembleDebug lintDebug` passed.
- 39 native tests: 17 telemetry, 5 startup/layout, 4 Tools actions, 6 session/lifecycle,
  and 7 socket/TLS tests; zero failures, errors or skips.
- Lint: zero errors, 92 warnings. Existing/development UI, deprecation and styling
  warnings remain; no baseline or disabled lint gate was added.
- Browser: all 13 panels, 20 functional groups and 39 navigation checks passed;
  no uncaught script errors.
- Real Android component render inspected; corrected unreadable spinner text.
- APK structure, bundled assets and signature verified. See signing continuity below.

An accelerated replay is not a two-hour physical battery/network test. No phone,
controller, flight controller or live ATAK instance was connected in this environment.

## Software validation performed October 2

- Prismo interface build: 41 native tests passed, including phone, landscape and tablet settings reachability and activity restoration.
- APK assembly, lint and native build evidence checks passed.
- Browser checks passed for 13 panels, 39 navigation checks and 20 functional groups with no uncaught script errors.
- Public manual and quick-start use committed software-rendered screenshots and reproducible ReportLab sources; PDF pages were rendered for visual review.
- Remaining physical acceptance below is unchanged.

## Remaining v1 acceptance

- [ ] Android phone clean install and upgrade; one Android tablet layout/install.
- [ ] Two real hours with screen off, app switching and normal power management;
      confirm source GPS and ATAK display remain correct.
- [ ] Twenty physical USB/network disconnect/reconnect cycles; no crash or stale
      position resurrection. Manual USB reconnect is expected.
- [ ] Real controller USB enumeration, permission and DTR, followed by measured aircraft
      GPS through controller and the same marker in ATAK. Historical RC capture is not GPS proof.
- [ ] Validate each advertised hardware/firmware/USB-mode combination separately.
- [ ] Real Android WebView checks of DEM import, export, clipboard and offline
      behavior across the 13 tools. Browser/Robolectric evidence does not replace this.
- [ ] Select and back up the long-lived release key; establish upgrade compatibility
      and record the signing certificate fingerprint.
- [ ] Publish a tagged, signed release APK with checksum, release notes and a public
      HTTPS asset URL; do not label the development/debug APK v1.

## Release signing

`assembleRelease` / `bundleRelease` refuse to run without all four variables:

- `TAK_RELEASE_KEYSTORE`: absolute path to the owner's long-lived signing keystore.
- `TAK_RELEASE_STORE_PASSWORD`
- `TAK_RELEASE_KEY_ALIAS`
- `TAK_RELEASE_KEY_PASSWORD`

Keep the key and passwords outside Git; back them up securely. The repository
cannot infer or recreate an existing release key. Debug builds use the local
Android debug key. An APK signed with a different key cannot update an installed
APK under the same package name; plan that transition before asking users to
install a production release. CI debug APKs may use a different ephemeral key
from locally delivered debug APKs and are not guaranteed drop-in upgrades.

Android foreground service reference:
https://developer.android.com/reference/android/content/pm/ServiceInfo#FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE

## Development signing continuity

The delivered 0.3.0 development APK uses certificate SHA-256
`2e127ebcae2cfa8e81e529327b98ea2758050120ce692001136413f0437413c9`.
The previously delivered 0.2.3 APK used
`b84593e751b12655f52e1a8c88e62d17ef3ab20283d0ae462ed4a463af913016`.
The previous temporary build key was no longer available; these APKs cannot be
installed as an in-place update. Export anything needed from the old installation
before uninstalling it; settings and imported certificates must be entered again.
The new development key is backed up privately as
`TAK-Bridge-development-signing.keystore` (not a production release key).
Future local development builds should set `TAK_DEBUG_KEYSTORE` to that restored
file. Never commit a signing keystore into this public repository.
