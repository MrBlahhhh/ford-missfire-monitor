# Build 1 validation — 2026-10-14

## Software checks

- 76 diagnostic JVM tests: Ford V8/V10 cylinder mapping, mode/units/limit decoding, missing vs zero/unevaluated results, live PID conversions, readiness/DTC/VIN parsing, protocol detection, ECU filtering, ISO-TP assembly, 1,000 malformed input samples, read-only allowlist, prompt timeout/overflow/echo handling and CAN PID bundles.
- 5 local-log JVM tests: CSV quoting/canonical units, unique session files, 20-session retention and both file caps continuing monitoring.
- Debug APK build and Android lint: required to pass before delivery; remaining warnings are English-only string localization.
- Tablet emulator: app launch, both vehicle profiles, no-adapter behavior and screen layout. No real phone installation or vehicle communication.

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
