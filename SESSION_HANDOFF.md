# Active Honda Tuner 1.1.5 from the latest phone log - 16 November 2026

The actual latest user request at 2026-11-16T02:21:23Z was recovered from
local history for thread 01a1e1f6-9d2a-7b32-b180-5c475693003a, after read_thread
omitted its failed turn: 1.1.4 succeeded; add visible sensor selection/order,
minimize live interval and investigate USB get_status request failed. These
requests remain active. Raw message and trace are retained privately in
the development mirror's build/phone-1.1.4-audit, not added to Git.

1.1.5-debug / versionCode 7 source is implemented: saved visible sensor order,
optional polling only selected groups, target 0 (no added delay), FTDI timer
1 ms, and FTDI vendor modem-status health checks for early empty bulk reads
instead of generic USB GET_STATUS. Actual I/O/status failures remain fatal.
Target/length/checksum/UART/echo validation, complete-frame quiet 24 ms,
cancelled RX deadlines and asynchronous hardware cleanup remain unchanged.
ID 72 00 00 is accepted along with 72 00 05 only in exact 11-byte valid frames.
The recorded ID 0102450F01 retains both Vario 150 catalog matches.

200 JVM regressions, 312 reference vectors / 5,616 values and 16 recorded
vectors / 166 values passed locally. Server APK build is still pending.
No physical 1.1.5 success or measured latency is established. The 1.1.4 log
contains three complete 29-field rounds (235–259 ms), a 22/24-byte truncated
main frame and recovery, then ends mid-line at entry 638. The reported generic
GET_STATUS error's failing transaction is absent; do not claim its physical
cause is proven. Evidence: evidence/phone_1_1_4_audit.json.

The prior 1.1.4 build 43788584478 succeeded on Linux runner 4 / ID31. All seven
assets, actual APK certificate/classes/catalog/source hashes and all 101
previous baseline fingerprints were verified. Its original assets and source
are now additionally preserved in dist/baseline-1.1.4 in both projects.
Preservation list: mirror build/phone-1.1.4-audit/preserved-baselines.json.
1.1.4 signer differs from 1.1.3. Compare new APK signer to 1.1.4 before delivery.

Authoritative checkout: honda_tuner_android/build/github-repo. Mirror:
honda_tuner_android. Source fixes, push, single server Actions build and APK
delivery are already authorized. Existing Linux runner 4 / ID31 is online.
Use terminal Git/gh directly, never Python wrappers. Per-command Codex /
codex@localhost author is allowed. Never run tools/prepare_repo.py, overwrite
historical ZIPs/baselines, register this laptop as a runner, or install/run a
local SDK/Gradle/APK toolchain. Existing JDK may run offline JVM tests.

Remaining: commit/push once, finish the server build, verify all seven assets
and source hashes, preserve baseline fingerprints, synchronize artifacts and
metadata, and deliver APK with installation and retest instructions.
