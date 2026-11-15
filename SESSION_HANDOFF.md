# Honda Tuner Android continuation — 15 November 2026

The requested first Android version uses direct USB + OTG for ECU connection,
continuous live data and current/stored DTC reading. Repository creation,
source upload, build and delivery were authorized by the user. Use direct
terminal `git` and `gh` commands; never wrap Git in Python.

The existing repository is https://github.com/FarizFx/honda-tuner-android.
The publishing checkout is `honda_tuner_android/build/github-repo` relative to
the workspace. Do not recreate/reset it or force-push over the user's edits.

Build and delivery are verified. Actions run `43683216038`, attempt 1, passed
on the user's **KillerCoda Linux server**, runner ID 24, at source commit
`22a4bba94ddd020e2325b388b5f09e3af3e03797`:
https://github.com/FarizFx/honda-tuner-android/actions/runs/43683216038

It compiled a signed debug APK and verified its signature, Android manifest
metadata and ZIP integrity. The APK is 61,358 bytes, with SHA-256:
`6bf9159335a26a7d8c81ac061e9c70065ee27cd6354a67623689f13b769c6d2e`.
All seven release assets were downloaded and their size/digest checked against
GitHub. The APK hash also matches the sidecar and successful CI report; a local
ZIP CRC check passed without installing Java or an Android SDK on the laptop.

Draft prerelease tag: `android-debug-43683216038-1`.
Owner-accessible release:
https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-d4cd86f485282c42a592
The APK and reports are in `dist/` in the development folder and publishing
checkout; `dist/` is ignored by Git. The historical source ZIP was preserved.

## Runner constraint and laptop cleanup

**Do not use the user's laptop as a self-hosted runner or install CI toolchains
on it without explicit approval for that use.** The user wanted server builds.
General permission to execute commands did not authorize making the laptop a
runner. The temporary laptop runner was stopped after the user cancelled run
`43681836445`; its registration and credentials were automatically removed.

At the user's cleanup request, `C:\Windows\TEMP\HondaTunerCI-20261115`
(about 828 MiB including the runner, portable Java 17/Python and SDK remnants),
the job-created pip cache, and four installer logs were moved to Recycle Bin.
No local runner process/service or CI environment/PATH entries were found.
The temporary files are absent from their original locations. They still use
disk space in Recycle Bin; permanent deletion was rejected by automatic command
review (`blocked by policy`). The pre-existing Python installation was retained
and still runs. The development folder records this in
`evidence/local_actions_cleanup.json`.

## Build corrections and verification

The user's self-hosted choice was preserved. The workflow validator no longer
requires Ubuntu or a fixed step count; it requires PyYAML. The primary workflow
supports Windows/Linux plus an optional runner-label input. Android CI is a
manual reusable-workflow wrapper. Default delivery is a draft prerelease because
Actions artifact storage is exhausted; artifact upload is optional and Gradle
Actions caching is disabled.

Windows SDK extraction uses an explicitly named ZIP and `sdkmanager.bat`.
Linux SDK setup requests `platform-tools`, omitting the removed legacy `tools`
package. The APK verifier checks Android 35 AAPT2's `minSdkVersion` field and
keeps the required API 26 minimum and API 35 target.

The real CI suite passed 33 JVM regressions, 312 decoder vectors and 5,616 field
values. Source/XML/YAML verification passed. Evidence records the verified run,
release, APK and its SHA-256; the README now reflects the successful build.

## Physical validation still outstanding

The application is API 26+ and uses an FT232R/FT232RL K-Line adapter, USB host and
OTG. The driver targets USB 0403:6001 / bcdDevice 0600, one UART at 10400 baud.
No physical phone, USB control-transfer/timing or ECU test has been performed.
No new REA runtime evidence is claimed. BIN/XDF, remap, DTC clearing and ECU
programming remain outside this first version. The preceding desktop builds
and pinned native binaries were not changed by this Android build work.
