# Android continuation — 15 November 2026

The user resumed the preceding chat. Its last unfinished request was an Android
version of Honda Tuner. The previous user choices were recovered from the session:
**direct USB + OTG**, initially **connection, live data and DTC reading**.
The user then explicitly requested a **new repo in their own GitHub account**,
source upload and APK build through GitHub. Do not ask those scope/creation
authorization questions again.

The proposed new repo name is `honda-tuner-android`, private by default. No repo
has been created, no source has been pushed, no Actions run has been started,
and no APK has been built. GitHub tools are absent from this session's tool list,
`gh` was not found on PATH or in the inspected tool locations, and the shell
cannot reach the configured network proxy. A final request to `api.github.com`
failed through proxy 127.0.0.1 as well.
Local SDK searches found no Android SDK; the JDK and offline JVM tests work.
These are external access/build-host blockers, not a request to pause work.

## Implemented source

- Native Android Java app, API 26+, compile/target API 35; Gradle 8.9 / AGP 8.7.3.
- Direct FT232R USB host driver for 0403:6001 / bcdDevice 0600, one UART;
  10400 baud, TXD bit-bang low 70 ms/high settling 130 ms, status removal from
  every USB packet and bounded, partial-aware bulk I/O.
- Original source-backed FE/FF plus diagnostic entry and optional identity;
  automatic ECU-returned sensor schema discovery and repeated 500-ms acquisition.
- Exact current echo and checksum/length/header validation; one-way wake sends
  once. Failed groups become unread; three total failures stop live.
- Core controller serializes connection/discovery/live/DTC. Stop, replacement
  session, detach and lifecycle changes invalidate queued/in-flight UI callbacks.
  Failed rediscovery revokes the old profile. DTC temporarily pauses/resumes live.
- Dark phone UI with sensor cards, measured RPM graph, current/stored DTC and
  catalog ID labels preserving ambiguity. No ECU write/clear command is exposed.
- `.github/workflows/build-android.yml` installs the toolchain, runs offline tests,
  builds/signs a debug APK, verifies signature/manifest/ZIP, and uploads artifacts.

## Verification and package

The final local suite passed **33 core/controller regressions**. `tools/test_core.py`
compiles actual core Java with `--release 8`, runs the real
scheduled workers/controller against UART fixtures, and compares 312 table-body
vectors / 5,616 values against the desktop decoder's saved output. Results and
source hashes are in `evidence/core_verification.json` and `core_tests.txt`.
`tools/verify_project.py` checks manifest/resource/configuration consistency and
parses workflow YAML when PyYAML is available. This is not Android API compilation.

`tools/prepare_repo.py` packages current, verified source as a root-ready ZIP at
`dist/honda-tuner-android-repo-20261115.zip`, with manifest and SHA-256 sidecar.
The external `evidence/repo_package_verification.json` verifies exact source-copy
hashes and archive integrity. Desktop application/engine source is unchanged.

## Next authorized actions when GitHub access is available

Create the new private repository in the authenticated user's account; check
name availability before choosing the final name. Upload only this Android
project's repo-ready source, including `.github` at repository root. Dispatch
`build-android.yml`, monitor the run, fix any actual Android/Gradle build failures,
and rerun as needed. Download and retain `HondaTuner-Android-debug` plus its
verification artifact; give the user the real repo/run/APK links.

Actual SDK/Android GUI compilation and FT232R control transfers still need their
first build/device validation. Existing JVM/fixture tests do not prove physical
USB timing, cold ECU connection or live readings on the user's motorcycle. No
physical adapter/ECU was opened during development, and no new REA Evidence ID
is claimed. Maps/remap and additional adapters remain outside the chosen scope.
