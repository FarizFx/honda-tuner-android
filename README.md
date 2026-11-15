# Honda Tuner Android

Aplikasi Android tahap pengembangan untuk koneksi ECU Honda lewat adaptor
FT232R/FT232RL K-Line dan USB OTG, live data otomatis, serta baca DTC saat ini
dan tersimpan.
Minimum Android 8.0 (API 26) dengan USB host.

**Status verifikasi per 15 November 2026:** tes core sudah lulus; hasil build
Android/APK belum terverifikasi.
Pengujian pada HP, adaptor USB, dan ECU fisik belum dilakukan.

Setelah izin USB diberikan, **Hubungkan** menjalankan inisialisasi K-Line dan
sesi diagnostik, membaca identitas ECU bila tersedia, mendeteksi sensor,
lalu memulai live data jika deteksi sensor berhasil. **Jeda live** menghentikan
pembacaan otomatis dan mempertahankan sampel terakhir.
**Baca DTC** menjeda live selama pembacaan; live dilanjutkan hanya jika
sebelumnya aktif.
Pergantian tab mempertahankan sampel. Saat aplikasi meninggalkan layar atau
adaptor dilepas, koneksi ditutup; tekan **Hubungkan** lagi untuk melanjutkan.

Live menargetkan satu putaran setiap **500 ms**. Putaran yang lambat menunggu
balasan ECU sampai selesai sebelum putaran berikutnya; pembacaan tidak saling
tumpang tindih. Sensor yang gagal dibaca ditampilkan **Belum terbaca**.
Live berhenti setelah tiga putaran berturut-turut tanpa grup data yang berhasil
dibaca.
Grafik memuat maksimal 50 titik pembacaan, termasuk celah saat RPM tidak terbaca.
Ukuran/versi tabel dipilih dari balasan ECU. ID katalog yang ambigu tetap
ditampilkan sebagai beberapa definisi yang cocok.

Driver diimplementasikan untuk USB **0403:6001**, revisi FT232R **0600**, satu kanal,
pada **10.400 baud**. Gunakan adaptor dengan rangkaian K-Line serta kabel/pinout
ECU yang sesuai. Kecocokan adaptor dan model ECU perlu diuji pada perangkat fisik.
Adaptor CH340, CP210x, Bluetooth, dan FTDI multikanal belum diimplementasikan.
Fitur BIN/XDF, remap, hapus DTC, dan pemrograman ECU belum tersedia dalam versi ini.

## Build APK di GitHub

Repo: [FarizFx/honda-tuner-android](https://github.com/FarizFx/honda-tuner-android).
Workflow ada di [.github/workflows/build-android.yml](.github/workflows/build-android.yml).

Build berjalan otomatis pada push/PR yang sesuai filter perubahan source di workflow.
Untuk menjalankan build manual, buka [Actions](https://github.com/FarizFx/honda-tuner-android/actions),
pilih **Build Android APK → Run workflow**, lalu pilih branch **main**.
Perubahan README saja tidak memicu build otomatis.

Input `runner` memilih label runner; nilai default adalah `self-hosted`.
Runner dengan label itu harus online. Workflow memakai Python 3.12 dan perintah
yang dapat dijalankan pada Windows maupun Linux. **Android CI** adalah pintasan
manual ke workflow utama.

Workflow memasang Python/PyYAML, Java 17, Gradle 8.9, Android platform 35 dan
build-tools 35.0.0; menjalankan
verifikasi source dan tes protokol; lalu menjalankan `:app:assembleDebug`.
APK yang berhasil dibuat harus lulus pemeriksaan tanda tangan, metadata dan
integritas ZIP sebelum disimpan sebagai **HondaTuner-Android-debug.apk**.

Pada branch `main`, hasil build disimpan sebagai **draft release**. Setelah run
sukses, buka tautan draft release di ringkasan job atau halaman **Releases** repo.
Pemilik repo dapat mengunduh berkas berikut:

- `HondaTuner-Android-debug.apk`
- `HondaTuner-Android-debug.apk.sha256`
- `apk_verification.json`
- laporan tes core, pemeriksaan proyek, dan asal fixture

Bandingkan SHA-256 APK dengan sidecar dan laporan verifikasi.
APK memakai tanda tangan debug dari runner untuk pengujian pemasangan.
Draft release menyediakan file hasil build ketika kuota Actions artifact penuh.

Input `upload_actions_artifacts` dapat diaktifkan untuk menambahkan artifact
**HondaTuner-Android-debug** dan **HondaTuner-Android-verification** di Actions
dengan retensi 14 hari. Nilai defaultnya `false`; aktifkan saat kuota penyimpanan
Actions tersedia.

Folder `dist/` diabaikan Git; APK dan arsip source di folder itu tidak disertakan
dalam repo. Status build terbaru tersedia di Actions. Catatan lokal ada di
[evidence/build_status.json](evidence/build_status.json).

## Build APK lokal

Build APK membutuhkan **JDK 17**, **Gradle 8.9**, **Android platform 35**, dan
**build-tools 35.0.0**. Tentukan lokasi SDK melalui `ANDROID_HOME` atau
`sdk.dir` dalam `local.properties`. Repo belum menyertakan Gradle Wrapper;
perintah berikut memakai Gradle yang sudah terpasang:

```sh
gradle --no-daemon --console=plain :app:assembleDebug
```

Hasil build berada di `app/build/outputs/apk/debug/app-debug.apk`.
Untuk memverifikasi dan menyalin APK ke `dist/`, gunakan Python 3:

```sh
python tools/package_apk.py --apk app/build/outputs/apk/debug/app-debug.apk --sdk "/path/to/android-sdk" --java-home "/path/to/jdk-17"
```

## Tes core tanpa SDK

Tes protokol tidak membutuhkan SDK Android atau ECU. Pemeriksaan workflow
membutuhkan PyYAML:

```sh
python -m pip install PyYAML==6.0.2
python tools/verify_project.py
python tools/test_core.py --java-home /path/to/jdk
```

`tools/test_core.py` mengompilasi source Java core yang dipakai aplikasi, lalu
menjalankan koneksi, framing/echo, dekoder, DTC, loop live dan controller sesi.
312 fixture dekoder dibandingkan dengan keluaran source desktop, meliputi
13 varian tabel dan 5.616 nilai field. Fixture adalah input terkontrol.
`tools/sync_reference.py` memperbarui schema, katalog dan fixture bila sibling
`../honda_tuner` tersedia; build GitHub memakai ekspor yang sudah disertakan.

`tools/prepare_repo.py` dapat membuat ZIP source lokal di `dist/` beserta
manifest hash setelah tes serta verifikasi source sesuai dengan berkas saat ini.
Folder build, SDK, runtime
Windows dan native engine tidak masuk paket repo.

## Status verifikasi

Per 15 November 2026, **33 tes regresi JVM** dan pemeriksaan struktur source
telah lulus lokal. Validasi dekoder mencakup **312 vektor / 5.616 nilai field**.
Catatan pengiriman mencatat repo dibuat dan source terupload ke GitHub.
Kompilasi Android lokal belum dijalankan; status run Actions dan APK belum terverifikasi.

Driver USB Android, waktu pulsa aktual, koneksi dingin, perubahan sensor,
dan DTC pada HP/ECU fisik belum diuji. Fixture memakai input terkontrol.
Laporan hasil, hash source yang diuji, dan asal schema ada di `evidence/`.
