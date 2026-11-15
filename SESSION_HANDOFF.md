# Active Honda Tuner Android connection repair - 16 November 2026

1.1.1-debug / code 3 built successfully on Linux runner 1 / ID 28, source
c7717c0a9c822185c27e7a8b6e577907c54651d0, run https://github.com/FarizFx/honda-tuner-android/actions/runs/43762436502.
76 JVM regressions, 312 reference vectors / 5,616 values, SDK compilation,
APK signature, ZIP CRC and lint (0 errors / 35 warnings) passed. All seven assets
were downloaded and matched their GitHub digest, sidecar and source reports.
Current dist APK SHA-256: b02bffd7fbebb90029b16ba62d3a26a9f0bed0d7fa4f95c384625c84cb272348.
Signer: 3e754a6e53eea394bb69c62aa4faea575316a2105c88f04f4b8da49c21dbff20. It differs from the older 1.1.0 signer.

User installed 1.1.1 and supplied a physical failure: Android 17 / API 37,
Xiaomi 2311DRK48G, FT232RL 0403:6001 / bcdDevice 0600, ECU Shindengen. Same adapter
and ECU connect directly in HondaECU Lite on PC. LOW FC / HIGH FF were confirmed,
but wake RX still includes hundreds of FF/FC GPIO samples with LSR 0x62. All six
entry attempts return 60 00 00 E0 10 for TX 72 05 00 F0 99. Do not claim this fix
established a physical ECU connection. Full trace and original reports/assets/source
are sealed in the development mirror's dist/baseline-1.1.1. Earlier baselines remain.

User says the K-Line board has 12 V and GND; FTDI is wired GND/VCC/RX/TX in 5 V
mode. Exact power routing is unknown. A delayed RX rising edge is only a hypothesis,
not a measurement or evidence that the user's power/wiring is faulty.

User asks to follow HondaECU and whether an Android FTDI driver can be installed.
Next: check FTDI's Android D2XX SDK and original retained transport oracle before
choosing a driver change. UART BREAK was proposed but has NOT been implemented.
Keep Honda 10400/8N1, 70 ms LOW / 130 ms HIGH timing, strict real ECU framing,
RX-only purge and no extra command after uncertain TX. Keihin and Shindengen scan
must not require a catalog identity. Exact motorcycle/ECU code remains unknown.

Authoritative checkout: honda_tuner_android/build/github-repo; development mirror:
honda_tuner_android. Source fixes, push, Linux server build and APK delivery are
already authorized. Never register the laptop as a runner or install Android/Java
toolchains locally. Offline tests may use existing D:/Tools/jre25/jdk-25.0.4.1+1.
Use terminal Git/gh directly, no Python wrappers. Commit identity may be supplied
per command as Codex / codex@localhost. Preserve runner 1/28, KC/27 and KillerCoda/26
and credentials. Do not run tools/prepare_repo.py: it overwrites a historical ZIP.
