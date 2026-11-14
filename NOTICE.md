# Reference data

Honda diagnostic serialization, table body schemas, field conversions, DTC
descriptions and exact ECU catalog labels are exported from the existing
independent Honda Tuner source in the sibling `honda_tuner` workspace.
Their retained source provenance includes earlier HondaECU Lite observations.
`evidence/reference_provenance.json` records the actual reference and export hashes.

The Android USB-host driver, Android UI, session controller, scheduled acquisition,
build workflow and JVM regression harness are new source for this continuation.
No original vendor executable, Windows runtime, DLL or native extension is bundled
in this Android repository or Android application. Android/Gradle build dependencies
are obtained by the build host and retain their respective terms.
