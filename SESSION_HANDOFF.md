# Active Honda Tuner 1.1.3 connection repair - 16 November 2026

1.1.3-debug / versionCode 5 source is implemented and locally verified: 141 JVM
regressions (83 existing, 21 transport, 13 lifecycle, 24 Lite fixtures), 312
decoder vectors / 5,616 values and project XML/YAML/source checks passed.
Source is not pushed yet; 1.1.3 APK is not built. Current dist APK remains 1.1.2.

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

Next: push this source, monitor the single 1.1.3 server build, download/verify
all seven release assets and compare installation signer with 1.1.2, then sync
the mirror and deliver APK. PHYSICAL_TEST.md covers direct OTG connection,
reconnect, at least 20 live rounds responding to real changes, pause/resume and
DTC. Physical success remains unverified until the user performs those tests.
