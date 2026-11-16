# Active Honda Tuner 1.1.4 sensor and identity repair - 16 November 2026

The user explicitly requested the fix and GitHub Actions build, then asked to
trace ECU ID/name reading and verify the embedded database. These requests were
recovered from the immediately preceding thread
01a1e1e4-9324-7193-a0d1-47909985d923. Source/push/server build/APK delivery are
already authorized. Ordinary source-repository work; no new native RE performed.

1.1.4-debug / versionCode 6 is implemented and locally verified: 186 JVM
regressions (89 core, 21 transport, 13 lifecycle, 22 UART, 24 Lite fixtures,
17 recorded USB transaction replays), 312 reference vectors / 5,616 values,
16 recorded vectors / 166 values, XML/YAML/source checks and Git diff checks.
Source is pushed at 7ae1d6b07e8281ee1d163b61e4c7de74c809574d.
One Actions job is queued:
https://github.com/FarizFx/honda-tuner-android/actions/runs/43788584478
All registered Linux runners are offline; an async question requests the user
to restart a server runner. APK 1.1.4 is not built or available yet.
Source ZIP: dist/source-1.1.4-7ae1d6b.zip, 205,783 bytes; SHA-256
7afa7731ff6bcc739aca94f8c642d49ffcb589bd7fce1ff9851c0e1e9fad60b2.
All 72 archived source files and 101 preserved baseline/archive hashes passed.
Local reports: build/repair-1.1.4/local in the development mirror. The previous
CI core/APK reports in evidence are still exact 1.1.3 server originals until
new originals are downloaded. Audit: evidence/uart_sensor_audit.json.

FTDI RxData and UartException retain every sample and enclosing packet LSR.
AUTO permits parity/framing/BREAK faults only within exactly TX-length echo,
followed by a fully clean valid ECU frame. Overrun is always rejected; a packet
crossing the echo/frame boundary taints ECU data. REQUIRED requires clean exact
echo; NONE requires clean direct frames. Full UART/frame/checksum/target/length,
ambiguity, 24 ms quiet, RX deadline/cancellation and fatal TX checks remain.

The user's 1.1.3 now connects and has continuous valid 0x20 polling, but had only
3 named sensors and blank ECU ID. Its complete USB windows replay as 16 accepted
frames plus one truly truncated D0, rejected. Main 0x17 has 13 named parameters,
D0 has 8, D1 has 5 and O2 0x20 has 3; combined profile has 29 named fields.
The trace lacks initial wake/ID. Do not infer physical cold-connection, changing
sensor values, sensor presence or the specific ECU ID failure cause from replay.

The old APK already contained 278 catalog rows / 245 IDs from desktop ecudb.json.
33 placeholder 9999999999 rows are now excluded from matching; the embedded
catalog has 245 definitions / 244 real exact IDs. Ambiguous 0102450F01 retains
both Vario 150 part numbers. ID query uses 72 07 72 00 00 05 10; replies require
11 bytes, payload 72 00 05 + five ID bytes. Optional failure is retained in
controller snapshots and Debug's Identitas/Model/Katalog header, beyond the
rolling wire log. No guessed ID, alternate protocol, flash or DTC clear added.

Authoritative checkout: honda_tuner_android/build/github-repo; development
mirror: honda_tuner_android. Source Git main/origin/main were e2e335b before edits.
Use terminal Git/gh directly, never Python wrappers. Per-command author Codex /
codex@localhost is allowed. Never run tools/prepare_repo.py or overwrite the
historical ZIP. Previous source snapshots and all baseline artifacts remain.
1.1.3 is newly preserved in dist/baseline-1.1.3 in both folders; 101 baseline and
archive fingerprints are in build/repair-1.1.4/preserved-baselines.json.

Build only on the user's existing Linux server. Runner 3 / ID30 was online, but
all registered runners are now offline. An async question requests that the user
restart a Linux server runner; keep the Actions job queued until one is online. Preserve every registration and
credential. Never register this laptop as a runner, install Android/Java/CI
here, or run a local APK build. The existing D:/Tools/jre25/jdk-25.0.4.1+1 was
used for offline Java tests only. Default push triggers one Actions build.
APK signer compatibility must be checked against the preserved 1.1.3 signer
764ff636cf91220f3b275cd0e2a03a44062b6824eaf6f9989174e834caada453 before delivery.

Offline-only build/repair-1.1.4/verify_delivery.py is prepared in the mirror.
It consumes run.json, jobs.json, release.json and assets/ produced by direct gh
commands; it does not invoke Git/gh, an SDK or USB/ECU I/O.
Remaining: monitor the queued run when a Linux server is online, resolve real CI failures,
download and compare all seven release assets/digests, source hashes, APK ZIP,
signature and version/classes/catalog, synchronize source/mirror/artifacts,
record delivery and provide APK. Full 1.1.4 physical qualification remains
outstanding and is covered by PHYSICAL_TEST.md.
