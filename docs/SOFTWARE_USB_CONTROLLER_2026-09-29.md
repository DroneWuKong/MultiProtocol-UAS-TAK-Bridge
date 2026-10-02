# USB controller compatibility software evidence — September 29, 2026

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

Fixtures cover actual-format GHST GPS, controller RC-only streams, separate CRSF GPS,
CRC rejection, split packets, signed and zero-truncated MAVLink 2, MAVLink 1,
source identity, reconnect reset, fresh fix requirements, stale-position CoT
rejection, XML escaping, locale handling and the no-poll controller profile contract.
The same parsing and publication code runs without hardware in these tests.

Build repair also resolved pre-existing source errors: a nested Kotlin comment,
missing CoT function, non-constant `const val` byte array, missing launcher icon,
and an unscoped coroutine `isActive`. Lint's optional phone-location permission
issue was repaired with declarations and explicit checks, without auto-requesting
permission or using phone GPS as aircraft position.

## Not measured here

No Android device, controller controller, radio, aircraft or ATAK instance was connected.
Android USB permission/DTR behavior and aircraft GPS through controller USB remain the
physical acceptance steps in [USB_CONTROLLER_SETUP.md](USB_CONTROLLER_SETUP.md).
The historical Operator capture proves controller USB RC streaming only; synthetic
GPS fixtures do not promote it to measured aircraft telemetry.

## 0.2.1 launch-crash follow-up

The user reported that 0.2.0 closed immediately after the launch animation.
A new Robolectric test with the real manifest/resources on API 35 reproduced:

```text
java.lang.NullPointerException: findViewById(...) must not be null
  at MainActivity.bindViews(MainActivity.kt:228)
  at MainActivity.onCreate(MainActivity.kt:184)
```

This startup ordering came from the pre-existing Map/Tools fragment code.
The original parser-only tests and successful APK build did not exercise it.
`executePendingTransactions()` inside activity `onCreate` does not guarantee
that a programmatically added fragment has created its view at that lifecycle
stage. Binding now runs from `MapFragment.onViewCreated` and uses its root view.
Restored fragments are reused, preserving tab navigation after recreation.

Checks after the fix: all 20 tests pass (17 telemetry + 3 Android startup tests).
The three new tests cover cold launch, Connect without hardware, and activity
recreation while Tools is selected followed by navigation back to Map. These
run entirely in software and are included in the existing Android CI command.
This reproduces and fixes the observed class of launch failure; no physical
phone or controller controller was connected to this test environment.

`assembleDebug` and `lintDebug` also pass (0 lint errors, 81 warnings).
APK 0.2.1 (version code 3) signature verifies and its signing certificate matches
0.2.0, so Android can install it as an update without uninstalling.

0.2.1 APK SHA-256:
`b16982db63d5ec7e702fd9841c5b4922cc4f43fd356a53c30f76ffd108fa2ff7`.
