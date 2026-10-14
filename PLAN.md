# Plan

## Build 1 implemented

- [x] Separate personal Android project, F-150 V8 and Winnebago V10 profiles.
- [x] Paired Bluetooth ELM327/OBDLink LX connection, bounded connect/transactions and lifecycle cleanup.
- [x] Engine-only CAN/legacy decoding; Mode 06 counts/averages with explicit unsupported/raw/unevaluated states.
- [x] Up-to-six Mode 01 CAN PID bundling with verified response coverage and single-PID fallback.
- [x] Live dashboard, readiness, stored/pending/permanent code reading.
- [x] Bounded local CSV/raw logging and user-chosen export, adaptive cylinder grid.
- [x] Diagnostic unit tests, APK build, lint and tablet-emulator smoke checks.

## Vehicle acceptance / next work

- [ ] Connect OBDLink LX to the 2004 F-150 and confirm CAN vs J1850 protocol, per-cylinder results and batch support.
- [ ] Connect LX to the Winnebago; verify actual Ford chassis model year/VIN, all ten cylinder IDs and returned units.
- [ ] Compare counts/weighted averages against a trusted scanner; log a known symptom without inducing a fault.
- [ ] Check ELM327 separately; clones may lack long-response/CAN formatting support.
- [ ] Export both vehicle debug transcripts. Add calibration-specific enhanced $22 counters only if supported by captured responses and a reliable definition.

Background recording, configurable gauges/graphs, freeze-frame decoding and full Torque feature parity are future features, not implemented claims. No device installation or vehicle communication has been performed by the agent.
