# Honda Tuner 1.1.6 RX-status repair and dashboard source ready

The user supplied the requested NEW 1.1.5 error log on this thread
01a1e278-b4ee-71a0-ad94-013208566654 at 2026-11-16T04:00:48.643Z. It reports
Status RX FT232R tidak tersedia (-1), with ID 0102450F01 correctly read and
four groups / 29 sensors, one complete round at 220.5 ms. The 65,535-character
excerpt has seven complete valid transaction summaries and 219 raw status-only
packets; it ends mid-line in transaction 84883 before the failing control probe.
The physical cause of the control failure is unproven. The older resubmitted
1.1.4 log was an exact duplicate, but it is NOT the latest user message anymore.
Raw latest log is private in build/ui-1.1.6/log-followup/user-1.1.5-message.txt.

RX repair is implemented: fresh valid FTDI packets/TX status suppress redundant
RX checks for 100 ms. A lone control result -1 is inconclusive and may recover
on subsequent RX; three misses at least 50 ms apart without new USB activity
remain fatal. Probes respect the remaining read budget. Short/malformed status,
I/O exceptions, uncertain TX, ECU UART/overrun/target/length/checksum/echo checks
stay strict. Reply quiet remains 24 ms, latency timer 1 ms, target 0 adds no delay.
Status-only packets are aggregated with count/last status/LSR flags; UART-bearing
packets stay exact. Salin akhir log keeps the original header and latest complete
entries within 120 entries / 32 KiB characters; Simpan .txt remains full trace.

The user deferred server activation/build until the error was checked. Audit
and source preparation are now complete. All six existing Linux runners are
offline. No 1.1.6 source push, Actions job, SDK compilation or APK exists yet.
Keep the build deferred until the user resumes it; do not create duplicate jobs,
register a laptop runner, install/run an Android toolchain locally, or alter
runner registrations/credentials. Existing server-build authorization persists.

Prepared 1.1.6 / versionCode 8 includes saved LCD/HUD sensor choices, theme,
digit size, mirror, landscape, RPM bar and focused polling. Rotation retains
USB and records. Min / Avg / Max cover every valid numeric sample including 0,
independently of the 600-sample chart/CSV limit, reset on disconnect and survive
pause/re-detection/layout changes. n= is removed. Refresh displays measured
monotonic completion intervals in ms (last eight periods), reading duration and
numeric 0 ms target. Re-detection resets cadence without resetting records.
Graph axes include labeled zero and avoid artificial negative RPM/TPS/voltage
bounds. Actual signed temperature/ignition data stay visible. VSS remains RAW
only where the ECU schema supplies it; recorded 0x17 has no VSS. Previews use
synthetic values and shared drawing code; they are not physical ECU measurements.

231 JVM regressions, 312 vectors / 5,616 values and 16 recorded vectors / 166
values passed on the exact current core/test source hashes. XML/YAML/Python
checks passed on final app/tool source. Android compile/lint/signature still
require the server. All 190 preserved baseline/archive fingerprints pass.
13 new regressions cover recovery/persistent failures, budgets, actual I/O,
logging, clipboard and a recorded 1.1.5 D0 frame with a synthetic empty read.
README, physical checks, evidence and manifest distinguish prepared 1.1.6 from
the exact original 1.1.5 CI reports and APK, which remain intact.

Authoritative checkout: honda_tuner_android/build/github-repo. Mirror:
honda_tuner_android. Local source/package receipt and final commit are in
build/ui-1.1.6/log-followup/prepared-source.json. GitHub main already contains
the old delivery-documentation commits at 5c592b12c98b33f1cd6de5103957cceb5f99555e;
the earlier HTTP 403 pending-documentation notices below are superseded.
Use terminal Git/gh directly; never invoke Git from Python or prepare_repo.py.

When the user resumes the server build, push the existing prepared source
commit once, monitor its one Actions run, and verify the actual HUD/classes,
catalog, APK signature versus 1.1.5, all seven asset digests and source hashes.
Download/synchronize/deliver the verified APK and update current build metadata.
Do not overwrite historical ZIPs/baselines. Latest verified APK remains
dist/HondaTuner-1.1.5-debug.apk from run 43793080151, SHA-256
aa979fe4f316ec4aceda863f449860380ce36046fa045440d2d3b284f604668a.

Earlier notes below are historical.

# Active Honda Tuner 1.1.6 dashboard update - 16 November 2026

The actual latest user request was recovered verbatim from failed-turn history
for thread 01a1e228-c8df-79c2-888c-f34794e818aa, timestamp 1794798960, followed
by "go" and "lanjut bro". The user reports 1.1.5 works and requests conditional
speed/VSS display, a configurable LCD speedometer/HUD and landscape mode,
Min / Avg / Max for the entire connected session with reset on disconnect,
no n= below sensors, actual refresh in milliseconds, and sensible chart axes
with visible zero labels. A new debug log has not yet been provided.

Implementation and verification are underway in build/github-repo. Original
1.1.5 APK/reports/source archives and previous baselines are preserved; baseline
1.1.5 is copied in both dist folders. Private request, fingerprints and local
reports: mirror build/ui-1.1.6. VSS currently has no verified km/h scale and is
absent from the recorded 0x17 schema; do not fabricate speed or units.

Existing authorization covers code changes, direct terminal Git/gh push and
Actions build on the existing Linux server, verification and local APK delivery.
Do not install or run an SDK/APK toolchain on this laptop, modify credentials
or runner registration, run tools/prepare_repo.py or overwrite old archives.
The 1.1.5 source already reached origin/main; two local delivery-documentation
commits are pending after HTTP 403. Git/gh read operations work on this turn.
Offline JVM verification may use the existing D:/Tools/jre25/jdk-25.0.4.1+1.

Earlier notes below are historical.

Delivery metadata push is pending: three direct Git attempts, including the
existing gh credential helper, returned HTTP 403. The application source at
443d36cc52ca282faeaadaebb2264682bb6602d1 is already on origin/main and produced
the successful verified 1.1.5 APK. Local main additionally contains the delivery
receipts/docs; do not rebuild the APK just to upload those records. No token,
credential, runner or security settings were changed. Retry the documentation
push when the Git route permits it. Local APK and exact CI reports are delivered.

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
