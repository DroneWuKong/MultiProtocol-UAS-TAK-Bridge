# Software fixtures

`generate_telemetry_fixtures.py` uses pymavlink 2.4.49 to produce independent
MAVLink 1/2 wire fixtures, including signed packets and zero-truncated payloads.
GHST fixtures use the field layouts documented and implemented by
Prismo-Operator at `2a569fbe0c03b31e7baafbcebccbeb63950ab3d5`:
`gateway/ghst_telemetry.py` and `gateway/tac_ghst_observer.py`.

The TAC RC sample here is synthetic, matching the captured address and frame
types. It is not the September 4 physical capture. No synthetic GPS output is
represented as evidence of TAC USB GPS support.

Run `./gradlew testDebugUnitTest assembleDebug`. These tests use no USB hardware,
Android device, TAK server, radio, or live vehicle. The Android USB host boundary
is `UsbSerialTransport`; pure byte ingestion and CoT formatting remain callable
without it. TAC profiles never send serial payloads. Only the explicit direct-FC
MSP profile permits the fixed MSP_RAW_GPS read request.
