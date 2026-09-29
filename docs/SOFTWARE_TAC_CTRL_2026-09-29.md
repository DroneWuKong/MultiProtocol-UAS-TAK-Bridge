# TAC.CTRL compatibility software evidence — September 29, 2026

Base: `0ee99facc78cea410bd694b57e02786ed77d3968`.
Operator reference: `2a569fbe0c03b31e7baafbcebccbeb63950ab3d5`.

## Executed checks

- Android `testDebugUnitTest`: 17 tests, zero failures/errors/skips.
- `assembleDebug`: successful, application ID `com.dronewukong.takbridge`,
  version 0.2.0 (code 2), minimum Android API 26, target API 35.
- Final `lintDebug`: 0 errors, 81 warnings (text, UI/accessibility,
  deprecations and existing project styling; no lint baseline or disabled gate).
- APK signature verification: passed (debug/test signing, APK v2 signature).
- `git diff --check`: passed.

Built APK SHA-256: `5907fa373b9e75f78236995af5367b932ad62bf2af7e3b55441d7433daff41c6`.
The APK is a test build, not a store release.

Fixtures cover actual-format GHST GPS, TAC RC-only streams, separate CRSF GPS,
CRC rejection, split packets, signed and zero-truncated MAVLink 2, MAVLink 1,
source identity, reconnect reset, fresh fix requirements, stale-position CoT
rejection, XML escaping, locale handling and the no-poll TAC profile contract.
The same parsing and publication code runs without hardware in these tests.

Build repair also resolved pre-existing source errors: a nested Kotlin comment,
missing CoT function, non-constant `const val` byte array, missing launcher icon,
and an unscoped coroutine `isActive`. Lint's optional phone-location permission
issue was repaired with declarations and explicit checks, without auto-requesting
permission or using phone GPS as aircraft position.

## Not measured here

No Android device, TAC controller, radio, aircraft or ATAK instance was connected.
Android USB permission/DTR behavior and aircraft GPS through TAC USB remain the
physical acceptance steps in [TAC_CTRL_SETUP.md](TAC_CTRL_SETUP.md).
The historical Operator capture proves TAC USB RC streaming only; synthetic
GPS fixtures do not promote it to measured aircraft telemetry.
