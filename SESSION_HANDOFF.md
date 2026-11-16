# Honda Tuner 1.1.7 free HUD editor APK delivered - 16 November 2026

The user's per-box drag/resize request and fullscreen HUD control complaint are
implemented, locally tested, built on Linux and delivered. The single source
push-triggered run succeeded; no duplicate workflow was dispatched.

Built source: 6fbbec0de7a048ee560bd68c836e35edacf55c54
Run: https://github.com/FarizFx/honda-tuner-android/actions/runs/43816932505
Actual runner: Linux 6 / ID33
Draft release: https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-80ca984ba24273fd9bd6
APK: dist/HondaTuner-1.1.7-debug.apk (1030433 bytes)
SHA-256: 7eb8c0d74c24ba0b36f6ee75c7fc01001186210aa236acb00f035f62f44933f1
Signer: c5db3c42e75a461bbb59205c03f081e4084e745bd87e72c34833312011af81f2
Same signer as 1.1.6: False
Installation: Simpan CSV/log yang diperlukan, uninstall 1.1.6, lalu pasang 1.1.7. Sertifikat debug berbeda; uninstall menghapus data privat dan pengaturan aplikasi.

243 regressions, 312 reference vectors / 5,616 values, 16 recorded vectors /
166 values passed on exact built source. SDK compile, signature, ZIP and lint
(0 errors / 35 warnings) passed on the server. Seven asset digests, 58
source hashes, actual geometry/editor/HUD/FTDI classes, catalog/license and
certificate versus 1.1.6 were independently inspected without an SDK locally.
All 373 immutable baseline/archive fingerprints remain preserved.

Open Live > Speedometer LCD, tap the canvas, then Atur HUD > Edit tata letak
or choose Edit tata letak directly. All nine blocks can move/resize, including
primary/three auxiliary cards, RPM bar, brand, status, refresh and VSS info.
Four handles resize width/height; blocks stay on canvas and can overlap. Kotak
menu selects covered blocks, changes card sensor and resets one block/current
orientation. Save commits position/size/layers/sensors; Cancel/Back restores
the draft. Portrait/landscape positions are separate and follow canvas size.
Editor is unmirrored; normal mirror returns on exit. Tap-only controls overlay
the normal fullscreen canvas and hide after 4 s, without moving its boxes.
Back leaves the editor first, then returns to Live without disconnecting USB.
Rotation keeps session statistics and completed draft placements. Six previews
use synthetic values with the real shared renderer. Physical touch/HP/ECU
behavior remains untested here and requires user retest. VSS stays RAW; no GPS,
inferred km/h or new wire mapping was added. Existing FTDI checks, records,
refresh semantics and strict data validation remain intact.

Authoritative checkout: honda_tuner_android/build/github-repo. Mirror:
honda_tuner_android. Private proof/helpers: build/ui-1.1.7/server-delivery.
Exact built archive: dist/source-1.1.7-6fbbec0.zip (267025 bytes),
SHA-256 59427191d7f011a8f75d7e34358df1b7cd48e89a314f7cd16798c583f2153249.
87 source files and 86 manifest entries are synchronized. CI reports are exact
server originals; delivery metadata is separate from the built source archive.
Keep previous ZIPs/versioned APKs/baselines, including baseline-1.1.6. Use direct
terminal Git/gh; never Python Git wrappers or prepare_repo.py. APK builds remain
server-only: no local Android SDK/Gradle toolchain or laptop runner; preserve
runner credentials/configuration. Existing JDK may run offline JVM checks.

Earlier notes below are historical.

# Honda Tuner 1.1.7 free HUD layout editor prepared - 16 November 2026

The user requested per-box drag and resize, continuing the complaint about fixed
HUD controls. 1.1.7 / versionCode 9 source now supports moving/resizing primary,
three auxiliary cards, RPM bar, brand, status, refresh and VSS info. Four corner
handles, stacking order, selection of covered boxes, per-card sensor choice,
Save/Cancel, reset of a box/current orientation, separate portrait/landscape
placement and draft restoration are implemented. The editor is unmirrored;
normal HUD mirror is restored on exit. Speedometer is fullscreen; tap opens
controls over the canvas and they hide after 4 s. Back cancels the editor first,
then returns to Live without USB disconnect. The editor works without an ECU.
No fabricated km/h, GPS or new sensor mapping is added. VSS remains RAW. The
existing FTDI repair and all protocol/validation/session records are retained.

243 regressions (146 core, 21 transport, 13 controller health, 22 UART, 24 Lite
and 17 packet fixtures), 312 reference vectors / 5,616 values and 16 recorded
vectors / 166 values passed locally on exact final core hashes. 12 new tests
cover bounds, corners, persistence, layers, hidden slots, orientations, draft
immutability, polling and resized drawing. Final structural checks passed.
Six previews use synthetic readings and the actual shared renderer. Android
SDK compilation, lint and signing still require the Linux server build.
373 immutable baseline/archive fingerprints remain preserved, including full
1.1.6 source/APK/reports copied in both dist/baseline-1.1.6 folders.

Authoritative checkout: honda_tuner_android/build/github-repo. Mirror:
honda_tuner_android. Private receipts/previews/local reports: build/ui-1.1.7.
Existing code/push/Actions/local APK delivery authorization persists. Linux
runner 6 / ID33 was online and idle at the last read; do not alter its config.
Commit/push the prepared source once, monitor its single push-triggered run,
download all seven assets and verify hashes, actual geometry/classes/catalog,
source hashes and signer versus 1.1.6. Synchronize verified artifacts/metadata
and deliver the versioned APK. Latest verified APK remains 1.1.6 from run
43807510648, SHA-256 e313a16f08f634ab882992ad782205348dd539b7f005ff7658e035bed04c1179.

Use terminal Git/gh directly; never Python wrappers for Git or prepare_repo.py.
APK builds are server-only: never install/run an Android SDK/Gradle toolchain
or register a runner on this laptop. Preserve runner credentials/registrations
and historical ZIPs/versioned APKs/baselines. Existing JDK may run offline JVM
checks. CI reports currently included are exact 1.1.6 originals; never label
them 1.1.7. Physical touch/phone/USB/ECU behavior remains for user retest.

Earlier notes below are historical.

# Honda Tuner 1.1.6 verified Linux APK delivered - 16 November 2026

The user resumed the build with 'udah nih bro, udah jalan runnernya'. The existing
prepared source was pushed once; its single push-triggered Actions run succeeded.
All required code, server build, asset verification and local delivery are complete.

Built source: e410957d0c0eeb8811f5b6f026f7cee665a5bcb1
Successful run: https://github.com/FarizFx/honda-tuner-android/actions/runs/43807510648
Actual runner: Linux 5 / ID32
Draft prerelease: https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-075c4938459d03ec5a68
APK: dist/HondaTuner-1.1.6-debug.apk (1017693 bytes)
SHA-256: e313a16f08f634ab882992ad782205348dd539b7f005ff7658e035bed04c1179
Signer SHA-256: 5d2c94c478d466ffec7757e65289a7493507120c8959ceb0b2081fa3d5bcf7b7
Same signer as verified 1.1.5: False
Installation: Simpan CSV/log yang diperlukan, uninstall 1.1.5, lalu pasang 1.1.6. Sertifikat debug berbeda; uninstall menghapus data privat dan pengaturan aplikasi.

231 JVM regressions, 312 reference vectors / 5,616 values and 16 recorded vectors /
166 values passed on the exact built source. SDK compilation, cryptographic APK
signature and lint (0 errors / 35 warnings) passed on the server.
All seven release digests, sidecar, ZIP CRC, 56 source hashes, actual
HUD/application/FTDI classes, catalog/license and certificate versus 1.1.5 were
verified locally without an Android toolchain. All 190 baseline fingerprints remain intact.

The latest 1.1.5 log reports RX status -1 after valid four-group / 29-field live
data, one complete round at 220.5 ms and successful ECU ID 0102450F01 (two catalog
matches). Its excerpt ends mid-entry 84883 before the failed control probe; the
physical cause is unproven. 1.1.6 suppresses redundant RX probes for 100 ms after
valid FTDI activity and tolerates one inconclusive -1, with three misses at least
50 ms apart without new USB activity remaining fatal. Short/malformed status,
I/O/TX failures and ECU UART/overrun/target/length/checksum/echo checks stay strict.
Latency stays 1 ms and reply quiet 24 ms. Status-only logs are aggregated;
Salin akhir log keeps the header and latest entries. Simpan .txt keeps full trace.

1.1.6 includes configurable LCD/HUD and landscape, primary and three auxiliary
sensors, colors/digit size/mirror/RPM bar/focused polling. Whole-session Min / Avg /
Max include valid zero, outlive the 600-sample chart limit and reset on disconnect.
Rotation, pauses and re-detection preserve records; n= is removed. Actual refresh
uses monotonic completion intervals in ms; target 0 adds no scheduling delay.
Graphs label zero without artificial negative RPM/TPS/voltage. VSS remains RAW,
only when provided by the ECU schema; recorded table 0x17 has no VSS. Preview
values are synthetic. Physical 1.1.6 phone/ECU behavior still requires user retest.

Authoritative checkout: honda_tuner_android/build/github-repo. Mirror:
honda_tuner_android. Private delivery proof and helpers: build/ui-1.1.6/server-delivery.
The exact built source archive remains dist/source-1.1.6-e410957.zip; delivery
metadata is updated separately. Prior versioned APKs/ZIPs/baselines are preserved.
Use direct terminal Git/gh; never prepare_repo.py or Python wrappers for Git.
Build APKs only on Linux servers; never install/run an Android SDK/Gradle toolchain
on this laptop, register a laptop runner or change runner credentials/configuration.
Existing JDK may run offline JVM checks when code changes need them. No ECU write
or device installation was performed. Current CI reports are exact server originals.

Earlier notes below are historical.

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
