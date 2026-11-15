# Reference data

Honda diagnostic serialization, table body schemas, field conversions, DTC
descriptions and exact ECU catalog labels are exported from the existing
independent Honda Tuner source in the sibling `honda_tuner` workspace.
Their retained source provenance includes earlier HondaECU Lite observations.
`evidence/reference_provenance.json` records the actual reference and export hashes.

The FTDI UART driver is usb-serial-for-android 3.12.0 (MIT), from
https://github.com/mik3y/usb-serial-for-android/tree/3.12.0.
Its copyright and license are included in
`app/src/main/assets/usb-serial-for-android-LICENSE.txt` and in the APK.
The Android adapter preserves raw FTDI status headers for the existing tested
packet codec. Honda wake timing, RX cleanup, TX completion and session control
remain application code. `evidence/ftdi_driver_reference.json` records the source
tag and artifact digest; Gradle verifies the driver's AAR checksum.

The Android UI, session controller, scheduled acquisition, build workflow and
JVM regression harness are new source for this continuation.
No original vendor executable, Windows runtime, DLL or native extension is bundled
in this Android repository or Android application. Android/Gradle build dependencies
are obtained by the build host and retain their respective terms.
