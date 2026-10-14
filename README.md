# Ford Misfire Monitor

Personal Android app for Matt's **2004 F-150 5.4L three-valve V8** and **2017 Winnebago Ford V10**, using a paired classic-Bluetooth **OBDLink LX** or compatible **ELM327**. Android 8.0+; build 1 / version 0.1.1. Separate project; no changes to the other vehicle apps.

## What it does

- Eight- or ten-cylinder dashboard with CAN Mode $06 current/last-cycle counts and 10-cycle weighted averages when the PCM returns them. Read time, unavailable results, unevaluated zero limits and unknown raw units are distinguished. The app does not convert a stored percentage into a count.
- Legacy Ford 2004 J1850 TID $53 per-cylinder stored misfire rates, when returned. These update when a DTC sets/clears, not on every misfire.
- RPM, coolant/intake temperature, bank 1/2 short/long fuel trims, engine load, speed and throttle. Display uses °F/mph; CSV preserves °C/km/h.
- Bundled CAN live polling: up to six supported Mode $01 PIDs per request, checked by PID identity and width regardless of response order. Missing/conflicting replies fall back to single-PID polling for that connection. A lost ELM prompt requires reconnection, not a speculative retry on an unsynchronized stream.
- Stored/pending/permanent engine code reading, readiness since clear, CAN VIN capture with profile-year mismatch notice, CSV and raw adapter transcript export through Android's file picker.
- Local logs only, no account/internet permission/ads. Last 20 sessions retained; CSV/transcript independently stop recording at 8 MiB while monitoring continues with a visible notice. Reconnect starts another file. Export closes/flushes the session before copying it.

## First use

1. Plug the adapter into the vehicle, switch ignition on, and pair it in Android Bluetooth settings. Close Torque/other scanner apps so only this app uses the adapter.
2. Install `app/build/outputs/apk/debug/app-debug.apk`. Grant Nearby devices permission.
3. Select the vehicle profile, refresh paired devices and select the LX/ELM327. Tap Connect. The app remembers the profile and adapter.
4. Check the displayed protocol, engine ECU and monitor readiness. Tap Read codes separately. The Winnebago's **Ford chassis year** can differ from the coach model year; check the VIN notice.
5. If counts are unavailable or raw units appear, export the debug log and CSV for that vehicle. These show the exact request, engine reply, units and thresholds needed to validate or extend the definitions.

Monitoring runs while the app is visible and keeps the screen awake. Leaving it closes the connection; returning requires Connect. This first version does not provide background logging or a full Torque feature set. Engine requests are allowlisted reads; there is no clearing/reset/relearn/programming control.

## Polling / data interpretation

CAN startup reads each cylinder once. Thereafter one cylinder is refreshed per roughly one-second polling cycle alongside two live-PID bundles, so a full eight/ten-cylinder sweep normally takes about 8/10 seconds, longer if the ECU responds slowly. Refresh all cylinders forces a complete sweep. Legacy TID $53 reads all cylinders approximately every five cycles. Each card shows its actual last-read age; live gauges blank after 15 seconds and cylinder results are marked stale after 30 seconds.

Counts are ECU monitor results, not a guaranteed instantaneous running total. The monitor may be disabled by operating conditions; readiness since clear is not an active-monitor flag. Nonzero counts are highlighted; a red limit indication uses the **ECU's returned limits**, never an invented universal alarm threshold. UASID $24 is decoded as one count per bit; unknown units remain raw for review.

Mode $01 bundling is different from combining Mode $06 actual monitor IDs. The latter is not supported by the standard request format used here; the app requests A2–A9 (V8) or A2–AB (V10) individually. Ford proprietary Mode $22 live counter definitions are not guessed or presented as verified.

## Build and verification

Use JDK 21+ for Gradle/AGP (Java source target 17) and the Android SDK platform 37. The wrapper pins Gradle 9.7.1 and AGP 9.4.0, matching the existing locally cached toolchain.

```powershell
.\gradlew.bat :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

See [validation](docs/VALIDATION.md), [definitions and sources](docs/FORD-DATA.md), [plan](PLAN.md) and [bug list](BUGS.md). The build is for first vehicle testing; actual adapter/ECU compatibility is not yet verified on Matt's vehicles.
