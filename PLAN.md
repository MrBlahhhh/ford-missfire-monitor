# Plan

## Build 1 implemented

- [x] Separate personal Android project, F-150 V8 and Winnebago V10 profiles.
- [x] Paired Bluetooth ELM327/OBDLink LX connection, bounded connect/transactions and lifecycle cleanup.
- [x] Engine-only CAN/legacy decoding; Mode 06 counts/averages with explicit unsupported/raw/unevaluated states.
- [x] Up-to-six Mode 01 CAN PID bundling with verified response coverage and single-PID fallback.
- [x] Live dashboard, readiness, stored/pending/permanent code reading.
- [x] Bounded local CSV/raw logging and user-chosen export, adaptive cylinder grid.
- [x] Diagnostic unit tests, APK build, lint and tablet-emulator smoke checks.

## Build 2

- [x] Review the first real phone/adapter transcript; separate Bluetooth success from ECU-search failure.
- [x] Add bounded temporary protocol retries, engine-response validation, failure-context logging and regression tests.
- [ ] Re-test the updated app with ignition on and the adapter connected to the vehicle; initial transcript contained no successful ECU replies.

## Build 3

- [x] Verify actual F-150 cylinder 5 results: weighted average 18, cycle count 0, UASID 01.
- [x] Correct count scaling and historical/current labels.
- [x] Add selected-cylinder load/new-count graph, bounded history and focused polling.
- [x] Test baseline/reset/gap handling and real captured reply decoding.

## Build 4

- [x] Default graph to all cylinders for the selected V8/V10 profile.
- [x] Display separate colored lanes with common time/scale and independent baseline/reset handling.
- [x] Poll full cylinder sweeps in all mode; interleave engine-load reads.
- [x] Add V8/V10 all-mode polling/history tests and emulator rendering checks.

## Vehicle acceptance / next work

- [x] Confirm 2004 F-150 CAN, per-cylinder result mapping and batch support from the saved ELM327-compatible session.
- [ ] Re-test the F-150 using the OBDLink LX specifically.
- [ ] Connect LX to the Winnebago; verify actual Ford chassis model year/VIN, all ten cylinder IDs and returned units.
- [ ] Compare counts/weighted averages against a trusted scanner; log a known symptom without inducing a fault.
- [ ] Check ELM327 separately; clones may lack long-response/CAN formatting support.
- [ ] Export both vehicle debug transcripts. Add calibration-specific enhanced $22 counters only if supported by captured responses and a reliable definition.

Background recording, general configurable gauges, freeze-frame decoding and full Torque feature parity remain future features. The load/misfire graph is implemented. The agent installed build 1 on Matt's phone and read its saved logs; no live vehicle commands were issued by the agent.
