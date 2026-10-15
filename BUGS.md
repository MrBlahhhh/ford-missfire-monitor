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

## Build 2 connection fix

- **CONNECT-01 fixed:** a valid adapter reply `A0` after failed ECU auto-search was mislabeled as an unsupported protocol. The failure now explains that Bluetooth connected but the engine ECU did not respond.
- **CONNECT-02 fixed:** initialization stopped after one automatic search. Bounded profile-specific temporary protocol attempts now require a valid engine bitmap; no data or a transmission reply cannot count as a connection.
- **LOG-01 fixed:** failed-start transcripts omitted the selected profile, adapter name and final exception. These are now recorded, along with the adapter's supply-voltage response.

Evidence: user-supplied session dated 2026-10-14 21:50 UTC. Bluetooth and adapter commands succeeded; the first `0100` returned `UNABLE TO CONNECT` and `ATDPN` returned `A0`. Matt reported the engine was off for that attempt. A later saved phone session shows successful 2004 F-150 CAN operation on the ELM327-compatible adapter.

## Build 3 graph/count fixes

- **COUNT-01 fixed:** the connected F-150 returned UASID 01 for TIDs 0B/0C. Those known Ford count results now use verified unsigned 1/bit scaling. Other unknown units remain raw.
- **LABEL-01 fixed:** both raw results looked identical, hiding that cylinder 5's 18 was the 10-cycle average while its last/current-cycle result was zero. Labels now identify each test explicitly.
- **GRAPH-01 implemented:** bounded engine-load timeline plus selected-cylinder count increases; baselines, counter decreases, unavailable data and long gaps cannot become false new-event spikes.

The successful session also establishes actual F-150 CAN and bundled live requests. The earlier failed search occurred with the engine off according to Matt; it does not establish an adapter fault. Build 2's retry/diagnostic improvements remain included.

## Build 4 all-cylinder graph

- Added distinct lanes for all eight/ten cylinders with a common time/amplitude scale and per-cylinder observed totals.
- All-cylinder mode is the default and selects a complete polling sweep; single-cylinder details remain available.
- Interleaved load reads improve load visibility during the sweep. Query timestamps remain explicit; no synchronized sampling claim is made.

## Build 5 review fixes (2026-10-14)

Whole-repo review. Nothing here was run on a vehicle.

- **CAN29-01 fixed:** with `ATH1`/`ATS1` the ELM327 prints a 29-bit ID as four bytes (`18 DA F1 10 06 41 00 ...`, ELM327 data sheet p46). `FrameDecoder` only accepted one 8-digit word, so every real 29-bit line was dropped and the V10's third handshake attempt (CAN 29-bit) could never pass, even with the engine answering. The test fixtures used the unspaced form, which an adapter with spaces on doesn't send. Both forms decode now. 11-bit lines are unchanged: their first word is always three digits.
- **LOG-02 fixed:** the `# SESSION` line of every debug transcript said `Build 2 / 0.1.2` through build 4, so LOG-01's build stamp was wrong in every log since build 3. It and the on-screen label now come from `BuildConfig`.
- **LIFE-01 fixed:** `configChanges` didn't cover `uiMode` or `smallestScreenSize`. Auto dark mode at sunset, a car dock, or resizing the window on the tablet recreated the activity; `onStop` then disconnected the adapter mid-drive and the new instance started with an empty graph. The views use fixed colors and rebuild in `onConfigurationChanged`, so nothing needs the recreate.

## Open acceptance gaps

- **VEHICLE-01:** F-150 CAN and bundled live replies are confirmed from the phone transcript. The Winnebago/LX, additional calibrations and load/misfire timing still need physical validation.
- **VEHICLE-02:** Mode $22 proprietary instantaneous counters are not implemented; available Mode $06 results may update slowly or remain unavailable. Need definitions/captures before extending.
- **DEVICE-01:** phone-specific Bluetooth permission/revocation, RF loss and background/foreground recovery still need testing on Matt's phone/tablet with the physical adapter.

UI is deliberately English-only for this personal build; lint translation warnings are documented, not suppressed as functional correctness findings.
