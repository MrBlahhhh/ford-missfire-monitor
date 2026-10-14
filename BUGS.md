# Bugs and validation gaps

## Fixed before build 1 delivery

- Echo stripping now removes an exact command line only; identical hex bytes inside a reply are preserved.
- API 26–29 uses compatible window insets; newer APIs are guarded.
- Incomplete/out-of-sequence CAN frames and cross-ECU fragments cannot produce a misfire count.
- Legacy misfire percentages cannot be displayed as event counts; unknown CAN scaling remains raw.
- Cancelled/retired connection callbacks cannot update another vehicle profile or reconnect session.
- A missing prompt closes the transport; blocked Bluetooth writes are bounded by a socket watchdog.
- Export waits for worker/log closure before opening the destination picker.
- Missing/duplicate/unknown-width bundled PID replies do not shift data into the wrong gauges; incomplete bundles fall back.
- Full local logs pause recording with a visible notice while monitoring continues.
- Legacy all-cylinder refresh no longer waits through empty per-cylinder iterations.

## Open acceptance gaps

- **VEHICLE-01:** no actual PCM replies from Matt's F-150/Winnebago or LX/ELM327 have yet been captured. Software tests and emulator checks do not validate hardware compatibility or calibration-specific PID availability.
- **VEHICLE-02:** Mode $22 proprietary instantaneous counters are not implemented; available Mode $06 results may update slowly or remain unavailable. Need definitions/captures before extending.
- **DEVICE-01:** phone-specific Bluetooth permission/revocation, RF loss and background/foreground recovery still need testing on Matt's phone/tablet with the physical adapter.

UI is deliberately English-only for this personal build; lint translation warnings are documented, not suppressed as functional correctness findings.
