# Active Honda Tuner 1.1.3 connection repair - 16 November 2026

1.1.3-debug / versionCode 5 source is implemented and locally verified: 141 JVM
regressions (83 existing, 21 transport, 13 lifecycle, 24 Lite fixtures), 312
decoder vectors / 5,616 values and project XML/YAML/source checks passed.
Source is pushed and APK is built/verified on Linux runner 2 / ID29.
Source: b9f0a189c4617596e7e8e85504a8a72dabc18975
Run: https://github.com/FarizFx/honda-tuner-android/actions/runs/43776385671
Release: https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-f766d1589a3cafb27ef6
APK: honda_tuner_android/dist/HondaTuner-1.1.3-debug.apk (991,509 bytes)
SHA-256: b4e356e4637e0538e07dc401d9f7196150c7e8c5d96da7830b2bd614fc2e0983
All 7 GitHub assets/digests, sidecar, reports, ZIP CRC, actual repair DEX classes,
62 source-hash checks and all 50 preserved baseline/ZIP fingerprints passed.
Server SDK/lint passed: 0 errors, 35 warnings. Core reports in evidence are exact
downloaded CI originals; local JVM reports remain in build/local-1.1.3-verification.
Debug signer differs from 1.1.2; save needed logs, uninstall 1.1.2, then install
1.1.3. Uninstall removes private app data. No app was installed/uninstalled here.

AUTO validates direct frames or full frames after exactly TX-length echo,
including damaged 60 00 00 E0 10 and 02 00 00 E0 10. REQUIRED checks exact echo,
NONE accepts direct frames. Target/length/checksum/UART/bounds stay strict.
Complete replies settle for 24 ms, bounded by the selected receive deadline,
to catch trailing fragments. Invalid prefixes do not trigger early retries.
Cancelled RX retains its original deadline; the next operation drains until it
ends before purging/TX. USB/partial or uncertain TX errors are fatal even after
operation cancellation. Old callbacks cannot close a replacement session.
Hardware cleanup is asynchronous; reopening waits for the old device cleanup.

FTDI 3.12.0, 10400/8N1, BREAK minimum 70/130 ms, RX-only purge, 60 ms wake drain
and pre-wake 24 ms quiet / 250 ms bound remain. Identity/catalog never gates scan.
Original HondaECU Lite Kline v3.5.99.30 observations and their explicit limits:
evidence/lite_transport_reference.json. Original native tests are memory FTDI,
not physical ECU traces. The user's 1.1.2 physically failed; diagnostic text is
evidence/phone-1.1.2-trace.txt, full user message is retained locally in build/.
Do not blame wiring, infer a measured rising edge or claim physical success.

Authoritative checkout: honda_tuner_android/build/github-repo; mirror:
honda_tuner_android. Source/push/server build/APK delivery are authorized.
Build only on existing Linux runner 2 / ID29; other runners/credentials stay.
Never register this laptop as a runner or install Android/Java/CI toolchains.
Existing D:/Tools/jre25/jdk-25.0.4.1+1 may run offline JVM tests. Git/gh directly
in terminal, no Python Git wrappers. Never run tools/prepare_repo.py; preserve
the historical ZIP and all baselines. Git identity can be per-command Codex /
codex@localhost. Metadata from the prior interrupted 1.1.2 delivery was present
before this continuation and is now corrected to include its physical failure.

Source repair, push, single-version server build and APK delivery are complete.
An asynchronous user question is pending for physical retest. PHYSICAL_TEST.md
covers direct OTG connection, reconnect, at least 20 live rounds responding to
real changes, pause/resume and DTC. Physical success remains unverified; do not
infer success from elapsed time, simulator tests, build, or APK integrity.
Historical source ZIP/baselines are untouched. New source snapshot is
dist/source-1.1.3-b9f0a18.zip; never reset from an older historical ZIP.
