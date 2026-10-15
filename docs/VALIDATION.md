# Build 4 validation — 2026-10-14

## Software checks

- 116 diagnostic/history JVM tests: Ford V8/V10 cylinder mapping, mode/units/limit decoding, missing vs zero/unevaluated results, live PID conversions, readiness/DTC/VIN parsing, protocol detection, ECU filtering, ISO-TP assembly, 1,000 malformed input samples, read-only allowlist, prompt timeout/overflow/echo handling and CAN PID bundles.
- 5 local-log JVM tests: CSV quoting/canonical units, unique session files, 20-session retention and both file caps continuing monitoring.
- Debug APK build and Android lint: required to pass before delivery; remaining warnings are English-only string localization and custom-view accessibility guidance.
- Tablet emulator: app launch, both vehicle profiles, no-adapter behavior and screen layout. Build 1 was installed on Matt's Pixel; actual F-150 CAN and bundled replies were verified from its saved logs. No live vehicle commands were issued by the agent.

## Build 2 regression validation

The submitted `UNABLE TO CONNECT` / `A0` transcript is reproduced in a scripted transport test. Twelve additional handshake tests cover automatic success, CAN/PWM/29-bit fallback, all attempts failing, transmission/wrong/truncated replies, rejected protocol commands and lost-prompt cancellation. Tests do not send anything to a real vehicle.

## Build 3

112 JVM tests across diagnostic parsing, protocol negotiation, local logging and graph history. The actual cylinder 5 reply is a regression fixture (no VIN/personal data). Graph tests cover initial historical values, positive deltas, unchanged/reset counts, per-cylinder isolation, 30-second gaps, unavailable results, out-of-order callbacks, local clear/disconnect and bounded retention.

Visual smoke check uses a separate instrumentation APK on an isolated emulator only. Synthetic chart samples never enter the release APK or vehicle session log. Rendering, graph summary and baseline/delta smoke checks passed. Physical load/misfire correlation remains an acceptance task.

## Build 4

121 JVM tests including all-eight/all-ten polling plans, no duplicate focus reads, invalid-profile handling and independent cylinder baselines/resets/unavailable results. Emulator rendering checks cover V8 and V10 lane layouts, including cylinders 9/10. Physical all-cylinder/load timing still depends on ECU response/update cadence.

## Build 5

125 JVM tests. New: spaced 29-bit headers in single and multi-frame replies, another 29-bit ECU filtered out, and the V10 handshake accepting a spaced 29-bit engine reply. Debug APK and lint pass. Not run on a vehicle or emulator; the 29-bit path still has no real capture.

## First vehicle check

1. Use OBDLink LX first. Select the right profile, ignition on, adapter paired, other scanner apps closed.
2. Confirm the app shows the engine ECU and CAN or legacy protocol; verify coach vs Ford chassis year notice.
3. Ensure eight/ten cylinder cards match the selected engine. Record current-cycle, weighted-average, unavailable or raw-unit results as actually returned.
4. Confirm bundled live polling indication and plausible RPM, °F coolant temperature and both banks' trims. If bundling falls back, export the debug transcript.
5. Read codes; unsupported permanent-code replies must show unavailable, not none. Check readiness semantics against the trusted scanner.
6. Switch ignition off/unplug adapter, then Disconnect/reconnect. Data must clear or mark stale; no old session may update a fresh one.
7. Switch vehicle/profile only after disconnect. Rotate the device and leave/return to the app; the connection must close when backgrounded.
8. Export CSV/debug log, inspect complete rows, timestamps, profile/ECU identity, MID/TID/UASID/raw values. Repeat on the other vehicle and on the ELM327 separately.

Do not claim these physical acceptance checks are complete until actual vehicle evidence is available.
