# Active Honda FTDI Android driver repair - 16 November 2026

Version 1.1.2-debug / code 4: source_pushed_build_failure. Source commit: 4f87381bb88a2d78f7b460c4cb1637d3528a4b08.
Run: https://github.com/FarizFx/honda-tuner-android/actions/runs/43766162568. 83 JVM regressions, 312 vectors / 5,616 values passed.
FTDI UART now uses usb-serial-for-android 3.12.0; Gradle checks AAR SHA-256
61221d698f71d2e4ac182e7b55ffeee17a01426ebb92e6e3c407b7c6df90f279.
Honda frames are unchanged. BREAK ON/OFF replaces original bit-bang LOW/HIGH,
holding at least 70/130 ms. Raw RX is drained before FE until 24 ms quiet, bounded
to 250 ms. Raw FTDI headers remain available for strict LSR/frame checks. Direct
RX purge request 0/value 1 avoids the upstream purgeHwBuffers RX/TX reversal.
No command follows uncertain TX until full initialization. See ftdi_driver_reference.json.

1.1.1 built successfully on runner 1/28, source c7717c0a9c822185c27e7a8b6e577907c54651d0,
run 43762436502, APK b02bffd7fbebb90029b16ba62d3a26a9f0bed0d7fa4f95c384625c84cb272348,
signer 3e754a6e53eea394bb69c62aa4faea575316a2105c88f04f4b8da49c21dbff20.
User installed it: Android 17/API 37, Xiaomi 2311DRK48G, FT232RL 0403:6001/0600,
ECU Shindengen. Same adapter/ECU works directly in HondaECU Lite on PC. Latest
trace has GPIO FF/FC samples (LSR 0x62); all six entry attempts return 60 00 00 E0 10
for TX 72 05 00 F0 99. Full original assets/reports/source/trace are sealed in
the development mirror's dist/baseline-1.1.1. Older baselines and historical ZIP remain.

User says K-Line board has 12 V/GND; FTDI is connected GND/VCC/RX/TX in 5 V mode.
Exact power routing unknown. Delayed RX HIGH remains a hypothesis, not a waveform
measurement or evidence that the user's wiring/power is faulty. User asks about
Android drivers and following HondaECU. D2XX exists; the downloaded sample license
restricts bundling/distribution, so no D2XX SDK was bundled. The chosen maintained
FTDI driver is MIT and its license is shipped in APK assets. Original retained
HondaECU oracle is honda_tuner/re/linux_transport_oracle.json. Physical success on
1.1.2 remains unverified. Keihin/Shindengen scan must not require a catalog ID.

Authoritative checkout: honda_tuner_android/build/github-repo; mirror: honda_tuner_android.
Push, source repair, server build and APK delivery are authorized. Never register
the laptop as a runner or install Java/Android/CI toolchains here. Build APK only
on existing Linux server. Existing JDK D:/Tools/jre25/jdk-25.0.4.1+1 may run offline
core tests. Terminal Git/gh directly; no Python Git wrappers. Commit identity may
be per-command Codex/codex@localhost. Preserve runner 1/28, KC/27, KillerCoda/26 and
credentials. Never run tools/prepare_repo.py; it overwrites a historical ZIP.

Current APK: 1.1.1 at dist/HondaTuner-Android-debug.apk; 1.1.2 is pending.
Remaining: monitor CI, fix actual failures, download/verify seven release assets, compare signer to 1.1.1, mirror and deliver APK.
