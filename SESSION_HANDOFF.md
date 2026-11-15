# Android continuation — 15 November 2026

The user resumed the preceding chat. Its last unfinished request was an Android
version of Honda Tuner. The previous user choices were recovered from the session:
**direct USB + OTG**, initially **connection, live data and DTC reading**.
The user then explicitly requested a **new repo in their own GitHub account**,
source upload and APK build through GitHub. Do not ask those scope/creation
authorization questions again.

The delivery record names `FarizFx/honda-tuner-android` at
`https://github.com/FarizFx/honda-tuner-android` and records successful repository
creation and source upload at commit `fc392c3f2bd83399abfc90789c8c913cd5997765`.
Private visibility was requested; current visibility has not been independently
verified. Do not recreate the repo or describe the recorded upload as unperformed.
Actions runs and APK results remain unverified, and no APK is present locally.
GitHub tools are absent, `gh` was not found, and direct Git/API requests fail at
the configured network proxy `127.0.0.1:9`.
Local SDK searches found no Android SDK; the JDK and offline JVM tests work.
These are external access/build-host blockers, not a request to pause work.

The publishing checkout is `honda_tuner_android/build/github-repo` relative to
the workspace root. The user explicitly requires plain `git` commands in the
terminal, without Python wrappers. Repository upload/build authorization persists.

## Implemented source

- Native Android Java app, API 26+, compile/target API 35; Gradle 8.9 / AGP 8.7.3.
- Direct FT232R USB host driver for 0403:6001 / bcdDevice 0600, one UART;
  10400 baud, TXD bit-bang low 70 ms/high settling 130 ms, status removal from
  every USB packet and bounded, partial-aware bulk I/O.
- Original source-backed FE/FF plus diagnostic entry and optional identity;
  automatic ECU-returned sensor schema discovery and repeated 500-ms acquisition.
- Exact current echo and checksum/length/header validation; one-way wake sends
  once. Failed groups become unread; three consecutive cycles without a valid
  group stop live.
- Core controller serializes connection/discovery/live/DTC. Stop, replacement
  session, detach and lifecycle changes invalidate queued/in-flight UI callbacks.
  Failed rediscovery revokes the old profile. DTC resumes live only if it was
  running before the read.
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

`tools/prepare_repo.py` can package verified source as a root-ready ZIP in
`dist/`, with manifest and SHA-256 sidecar. The original source ZIP in the
development folder remains a snapshot from before the documentation corrections;
`dist/` is not tracked in the publishing checkout. The corrected README explains
the unverified build status, SDK requirements, artifact paths, DTC resume rule,
failure threshold, and the graph's 50-entry limit. The application source is
unchanged by these corrections.

## Next authorized actions when GitHub access is available

Push the pending documentation corrections directly with Git. Inspect the
existing Actions runs for the recorded source commit; dispatch `build-android.yml`
if needed. Monitor the build, fix actual Android/Gradle failures, and rerun as
needed. Download and retain `HondaTuner-Android-debug` plus its verification
artifact; give the user the real repo/run/APK links. README changes alone do not
match the workflow's automatic push filter.

Actual SDK/Android GUI compilation and FT232R control transfers still need their
first build/device validation. Existing JVM/fixture tests do not prove physical
USB timing, cold ECU connection or live readings on the user's motorcycle. No
physical adapter/ECU was opened during development, and no new REA Evidence ID
is claimed. Maps/remap and additional adapters remain outside the chosen scope.
