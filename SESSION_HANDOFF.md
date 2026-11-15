# Honda Tuner Android continuation — 15 November 2026

Active request: APK 1.0.0 detects USB but cannot scan the ECU; no debug log was
visible. Audit/fix connection logic, expand live-data UI, add visible logging,
push, build on the server and deliver a new APK. Motor/ECU, adapter and Android
version remain unknown. Do not claim ECU success before the user's retest.

Repo: https://github.com/FarizFx/honda-tuner-android (public).
Checkout: `honda_tuner_android/build/github-repo` in the workspace.
Mirror: `honda_tuner_android`. Verified source, APK and reports are synchronized.
Never reset from or overwrite the historical source ZIP.
Run `git` / `gh` directly in the terminal; never wrap Git in Python.
Changes, push, server build and delivery are authorized. No delegation requested.

## Build constraint

**Never register/run this laptop as a self-hosted runner or install CI toolchains
on it without explicit permission.** Builds use the user's KillerCoda Linux server.
Successful update used runner ID **27**, name **KC**, labels self-hosted/Linux/X64.
Old runner ID 26, KillerCoda, is offline. Preserve registrations and credentials.
The pre-existing JDK `D:\Tools\jre25\jdk-25.0.4.1+1` may run offline core tests.
Existing Python: `C:\Users\Fariz\AppData\Local\Programs\Python\Python312\python.exe`.
Do not install an SDK or compile APKs on this laptop.
The previously cancelled laptop runner/toolchain was recycled after permanent
deletion was auto-review rejected. Do not recreate it.

## Current update

Version 1.1.0-debug / code 2. Server core passed 57 regressions, 312 vectors and
5,616 field values. Structural/XML/YAML, SDK compilation, lint and APK checks passed.
Built source is pushed to main at `9913287bb961ceb45131950b7ef0c437805d2da6`
(implementation commit `d2d5867c55e0468cfe65ae8287d2654ec521976a`).
Build `43730532722` succeeded on KC:
https://github.com/FarizFx/honda-tuner-android/actions/runs/43730532722
Run `43723698241` failed with two lint errors: API 27 windowLightNavigationBar in
the API 26 theme and an unflagged legacy registerReceiver call. Commit `f90d638`
removed the redundant theme item and exposed all fatal lint findings in Actions.
Run `43730129943` exposed the remaining receiver error; commit `9913287` switched
to the API 26 flags overload with guarded API 33 RECEIVER_NOT_EXPORTED. No lint
suppression or minSdk increase. Final lint: 0 errors, 35 nonfatal warnings
(28 SetTextI18n, 5 UnusedResources, 1 DrawAllocation, 1 DataExtractionRules).
The development mirror is synchronized: 54 tracked files verified by SHA-256;
previous files are backed up under ignored build/mirror-backup-*.
The obsolete mirror RpmGraph.java was backed up and removed.
All seven draft-release assets match their GitHub sizes/digests. APK also matches
the CI report and SHA sidecar, and its downloaded ZIP CRC passed.
`dist/HondaTuner-Android-debug.apk` is now 1.1.0-debug, 97,142 bytes; SHA-256:
`79cf396aa7bbad2d252158ea983c013c30d55c04c04731897c352bc0d21406f8`.
Tag `android-debug-43730532722-1`; draft release (owner access):
https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-6ec9611160a1c9158ec1
Signer SHA-256: `f348ce46260b58314a30b2029e238759758041648f9f6c1556f5fed7e453527a`.
This differs from baseline. Save needed data, uninstall the old app, then install
the new APK; uninstall removes private app data. No APK was installed by this task.
Text exports and verification reports now use LF consistently across hosts.

- AUTO accepts exact TX echo plus valid ECU reply or direct valid reply. Strict
  frame/checksum checks remain; wake/echo never establish ECU connection.
- Status-only FTDI BREAK/error packets are ignored; actual UART data errors reject.
  Disable flow control explicitly and settle HIGH before UART mode.
- Full init/wake/entry retries up to three times with progress/cancellation.
  Optional identity failure is nonfatal.
- Successful fallback groups discard obsolete candidate failures.
- Runtime polling failures become unread samples and stop after three failures.
  Transport construction failures close unowned channels; stale work is revoked.
- Shared bounded trace records USB/configuration, stages, TX/RX, errors and stack.
  Debug exposes current/last-error, copy/save/detail and echo/timeout settings;
  errors persist privately.
- Four tabs: Live/Grafik/DTC/Debug. All named discovered sensors, search/category,
  numeric min/max/average, actual frequency, selectable 120-point time graph,
  interval, pause/resume/manual read and 600-sample CSV.
- Export snapshot precedes document picker; foreground exit closes UART.
  Permission result can be consumed on return without a background USB session.
  Collapsible connection panel supports short screens.
- No permissions, remap/programming or DTC clearing were added.

Only verified RPM/V/°C/° units; unscaled pressure, VSS, injector and other signals
remain raw. Support stays at FT232R 0403:6001 / bcdDevice 0600 / one UART.
Wake is LOW 70 ms, HIGH settling 130 ms; 10400 baud 8N1.
Logged host timing is not proof of physical pin timing.

## Remaining device validation

Build and delivery are complete. Ask the user to retest Hubungkan on the phone
using 1.1.0-debug. If ECU scan still fails, Debug opens automatically; use
Salin log / Simpan .txt for the trace. Device model, ECU, adapter and Android
version are still unknown. Physical ECU success remains unverified.

## Baseline

KillerCoda runner ID 24 build:
https://github.com/FarizFx/honda-tuner-android/actions/runs/43683216038
Source `22a4bba94ddd020e2325b388b5f09e3af3e03797`.
Tag `android-debug-43683216038-1`.
Release:
https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-d4cd86f485282c42a592
APK 61,358 bytes, SHA-256:
`6bf9159335a26a7d8c81ac061e9c70065ee27cd6354a67623689f13b769c6d2e`.
Signer SHA-256:
`f950d93b5f8afcf06e1b75d4aacfc290ca77341ebbb390fdfabb80ce3f30ae51`.
Baseline assets preserved in ignored `dist/baseline-1.0.0`; historical ZIP intact.

## Workflow

Python 3.12/PyYAML, Java 17, Gradle 8.9, AGP 8.7.3, SDK 35.
Install platform-tools instead of retired tools. Draft prerelease is default
because artifact storage is full; artifact uploads optional, Gradle cache disabled.
Package verifier checks signature/ZIP, SDK 26/35, app version and Android lint XML.
