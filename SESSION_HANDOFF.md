# Active Honda Tuner Android wake repair — 16 November 2026

Recovered the immediately preceding failed session and its user trace. Android 17
(API 37), Xiaomi 2311DRK48G, FT232RL 0403:6001 / bcdDevice 0600. The user's ECU is
Shindengen; the same adapter/ECU connects directly in HondaECU Lite on PC. The
requested scan also covers Honda Keihin, without requiring an ID in the catalog.
Exact motor and ECU code are still unknown; do not repeat the already answered questions.

Version 1.1.1-debug / code 3 is implemented. 76 JVM regressions, 312 reference
vectors / 5,616 field values and structural/XML/YAML checks passed offline with
the pre-existing JDK. Source commit: prepared; push pending.
Server run: not triggered yet.
Current 1.1.1 APK is not built. A newly registered Linux server runner named 1
(ID 28, self-hosted/Linux/X64) is online. KC 27 and KillerCoda 26 remain offline.
Preserve all three registrations, labels and credentials.
Never register this laptop as a runner or install SDK/Java/CI toolchains here.
Build only on the user's Linux server. Direct terminal git/gh only; no Python Git wrappers.
Source fixes, push, server build and APK delivery are already authorized.

Changes: RX-only purge, conservative UART wire guard plus fresh THRE/TEMT polls,
no retry appended after uncertain TX, confirmed bit-bang LOW/HIGH before changing
mode, 70/130 ms hold/settle, and idle restoration on failed/cancelled setup/close.
AUTO/NONE one-way wake drains up to 60 ms after TX completes and records discarded
UART data errors. USB transport errors and real diagnostic/sensor/DTC replies
remain strict. Connection still needs a valid diagnostic-entry frame. A red test
reproduced the user's exact 01 68 FC 00 packet before the fix; see wakeup_audit.json.
No remap/ECU writes/DTC clearing was added. Physical cause of FE -> FC remains
unproven; pin/status reports do not measure K-Line at the ECU connector.

Checkout: honda_tuner_android/build/github-repo. Development mirror is
honda_tuner_android. Sync status is in evidence/build_status.json. Local metadata
helper build/record_wakeup.py only copies/hashes files; it never invokes Git/gh.
It needs build/wakeup-source-files.txt produced by direct git ls-files.
Current APK path still contains 1.1.0-debug, SHA-256 79cf396aa7bbad2d252158ea983c013c30d55c04c04731897c352bc0d21406f8.
That APK and all original release assets/reports are preserved in
dist/baseline-1.1.0. The older baseline-1.0.0 and historical source ZIP are intact.

Remaining: Actions compile/lint/package success, download
and verify all release assets, synchronize/deliver the 1.1.1 APK, then user retests
Hubungkan and supplies the new Debug log if it fails. Do not claim a new APK or
physical Keihin/Shindengen success while this is pending.
