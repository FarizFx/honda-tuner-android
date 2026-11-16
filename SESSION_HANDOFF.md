# Honda Tuner 1.1.5 verified server APK delivered - 16 November 2026

Actual latest user log and requests were recovered from local history of
thread 01a1e1f6-9d2a-7b32-b180-5c475693003a at 2026-11-16T02:21:23Z. The user
reported 1.1.4 succeeds, requested sensor visibility/order and fastest practical
live acquisition, and reported USB GET_STATUS failure plus empty ECU identity.
All source changes, push, Linux server build and local APK delivery are complete.

Built source: 443d36cc52ca282faeaadaebb2264682bb6602d1
Successful run: https://github.com/FarizFx/honda-tuner-android/actions/runs/43793080151
Actual runner: Linux 4 / ID31
Draft prerelease: https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-eb8eb899874d28f4ba85
APK: dist/HondaTuner-1.1.5-debug.apk (999321 bytes)
SHA-256: aa979fe4f316ec4aceda863f449860380ce36046fa045440d2d3b284f604668a
Installation: Pasang 1.1.5 sebagai pembaruan 1.1.4; sertifikat debug sama.

200 JVM regressions, 312 reference vectors / 5,616 values and 16 recorded
vectors / 166 values passed on the server. SDK compilation, cryptographic APK
signature, lint (0 errors / 36 warnings), all seven GitHub asset digests,
sidecar, ZIP CRC, actual repair classes/catalog/certificate and source hashes
were verified. 123 original baseline/archive fingerprints are preserved.
The first CI attempt 43792852031 failed on a missing Comparator import used by
USB sorting. The import was restored and this final source built successfully.

Sensor visibility/order persists across starts and ECU schemas, and edits do
not reset history. Optional focus reads only selected groups; unpolled groups
are unread in history/CSV. Target 0 adds no scheduling delay after a serialized
round; 50/100/250/500/1000/2000 ms remain available. Old saved intervals migrate
by actual value. FTDI latency timer is 1 ms; reply quiet stays 24 ms.
Early empty bulk reads use bounded FTDI vendor status instead of generic
GET_STATUS. Short/failed status and USB/TX exceptions stay fatal. ID headers
72 00 00 and 72 00 05 require exact complete 11-byte valid frames; recorded ID
0102450F01 preserves both Vario 150 names (K59F-A01 and K59A-A11).

1.1.4 log has three 29-field complete rounds (235–259 ms), a 22/24-byte
truncated main reply followed by recovery and an excerpt ending mid-line after
entry 638. It lacks the failing USB transaction; its physical cause is unproven.
Full 1.1.5 physical qualification and latency measurement remain for user retest.
PHYSICAL_TEST.md covers sensor selection/persistence, fastest/focused polling,
ID, USB loss/reconnect, live values and DTC. No device installation, ECU write,
local SDK/toolchain build or runner configuration change was performed here.

Authoritative checkout: honda_tuner_android/build/github-repo. Mirror:
honda_tuner_android. Use terminal Git/gh directly; no Python wrappers.
Never run tools/prepare_repo.py or overwrite historical ZIPs/baselines. Build
only on existing Linux servers; never register this laptop or install an APK
toolchain here. Existing JDK can run offline JVM tests. Raw user log, receipts,
failed CI log, local verification and offline delivery verifier remain privately
in mirror build/phone-1.1.4-audit. CI reports in evidence/dist are exact server
originals. Baseline 1.1.4 is preserved in both dist/baseline-1.1.4 folders.
