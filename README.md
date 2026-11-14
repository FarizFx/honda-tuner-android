# Honda Tuner Android

Aplikasi Android untuk koneksi ECU Honda lewat adaptor FT232R/FT232RL K-Line
dan USB OTG, live data otomatis, serta baca DTC saat ini dan tersimpan.
Minimum Android 8.0 (API 26) dengan USB host.

Setelah izin USB diberikan, **Hubungkan** menjalankan inisialisasi K-Line dan
sesi diagnostik, membaca identitas ECU bila tersedia, mendeteksi sensor,
lalu memulai live data. **Jeda live** menghentikan pembacaan otomatis.
**Baca DTC** menjeda live selama pembacaan dan melanjutkannya setelah selesai.
Pergantian tab mempertahankan sampel. Saat aplikasi meninggalkan layar atau
adaptor dilepas, koneksi ditutup; tekan **Hubungkan** lagi untuk melanjutkan.

Live menargetkan satu putaran setiap **500 ms**. Putaran yang lambat menunggu
balasan ECU sampai selesai sebelum putaran berikutnya; pembacaan tidak saling
tumpang tindih. Sensor yang gagal dibaca ditampilkan **Belum terbaca**.
Tiga putaran gagal berturut-turut menghentikan live. Grafik menyimpan 50 sampel
RPM yang benar-benar diterima, dan memberi celah saat RPM tidak terbaca.
Ukuran/versi tabel dipilih dari balasan ECU. ID katalog yang ambigu tetap
ditampilkan sebagai beberapa definisi yang cocok.

Driver pertama mendukung USB **0403:6001**, revisi FT232R **0600**, satu kanal,
pada **10.400 baud**. Adaptor CH340, CP210x, Bluetooth, dan FTDI multikanal
belum diimplementasikan. Fitur BIN/XDF, remap, hapus DTC, dan pemrograman ECU
berada di luar lingkup versi pertama yang dipilih pengguna.

## Build APK di GitHub

Workflow ada di `.github/workflows/build-android.yml`. Isi proyek ini menjadi
root repo `honda-tuner-android`; folder `.github` harus berada di root repo.
ZIP `dist/honda-tuner-android-repo-20261115.zip` berisi struktur tersebut tanpa
folder pembungkus.

Di repo GitHub, buka **Actions → Build Android APK → Run workflow**.
Workflow juga berjalan pada push/PR yang mengubah source. Workflow memasang
Java 17, Gradle 8.9, Android platform 35 dan build-tools 35.0.0; menjalankan
verifikasi source dan tes protokol; lalu menjalankan `:app:assembleDebug`.
APK yang berhasil dibuat harus lulus pemeriksaan tanda tangan, metadata dan
integritas ZIP sebelum menjadi artifact **HondaTuner-Android-debug**.
Artifact mencakup `HondaTuner-Android-debug.apk`, SHA-256, dan laporan verifikasi.
Artifact **HondaTuner-Android-verification** menyimpan hasil tes dan pemeriksaan.
APK memakai tanda tangan debug dari runner untuk pengujian pemasangan.

Pembuatan repo, push dan eksekusi Actions membutuhkan akses ke akun GitHub.
Catatan status sesi ada di `SESSION_HANDOFF.md` dan `evidence/build_status.json`.

## Build lokal dan tes tanpa SDK

Buka folder ini di Android Studio dengan JDK 17 dan Android SDK 35, atau gunakan
Gradle 8.9:

```sh
gradle --no-daemon :app:assembleDebug
python tools/package_apk.py --apk app/build/outputs/apk/debug/app-debug.apk
```

Tes protokol tidak membutuhkan SDK Android atau ECU:

```sh
python tools/verify_project.py
python tools/test_core.py --java-home /path/to/jdk
```

`tools/test_core.py` mengompilasi source Java core yang dipakai aplikasi, lalu
menjalankan koneksi, framing/echo, dekoder, DTC, loop live dan controller sesi.
312 fixture dekoder dibandingkan dengan keluaran source desktop, meliputi
13 varian tabel dan 5.616 nilai field. Fixture adalah input terkontrol.
`tools/sync_reference.py` memperbarui schema, katalog dan fixture bila sibling
`../honda_tuner` tersedia; build GitHub memakai ekspor yang sudah disertakan.

`tools/prepare_repo.py` membuat ZIP source dan manifest hash setelah tes serta
verifikasi source sesuai dengan berkas saat ini. Folder build, SDK, runtime
Windows dan native engine tidak masuk paket repo.

## Status verifikasi

Tes JVM dan pemeriksaan struktur source telah dijalankan lokal. Kompilasi API
Android dan workflow GitHub belum dijalankan pada sesi ini karena SDK lokal
dan akses GitHub belum tersedia. Driver USB Android, waktu pulsa aktual,
koneksi dingin, perubahan sensor dan DTC pada HP/ECU fisik belum diuji.
Laporan hasil, hash source dan asal schema ada di `evidence/`.
