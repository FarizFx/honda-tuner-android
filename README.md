# Honda Tuner Android

Diagnostik ECU Honda lewat adaptor FT232R/FT232RL K-Line dan USB OTG.
Minimum Android 8.0 (API 26) dengan USB host.

**v1.1.0-debug:** APK sudah dibangun di server Linux **KC** pada
[run 43730532722](https://github.com/FarizFx/honda-tuner-android/actions/runs/43730532722).
Build, lint (**0 error**), signature dan ZIP lulus; **57 tes regresi JVM**,
**312 vektor dekoder** dan **5.616 nilai field** juga lulus.
Pada APK 1.0.0, pengguna berhasil mendeteksi USB, tetapi scan ECU belum berhasil
dan log debug belum tersedia di UI. Pembaruan ini memperbaiki penanganan echo,
status FTDI, retry dan izin USB serta menambahkan trace yang bisa disalin.
Komunikasi ECU masih perlu diuji ulang pada HP.

APK tersedia di [draft release untuk pemilik repo](https://github.com/FarizFx/honda-tuner-android/releases/tag/untagged-6ec9611160a1c9158ec1)
dan `dist/HondaTuner-Android-debug.apk` pada folder proyek lokal.
Sertifikat debug runner KC berbeda dari APK 1.0.0: simpan data yang dibutuhkan,
uninstall versi lama, lalu instal APK baru. Uninstall menghapus data privat aplikasi.
Versi **1.1.0-debug** tampil di bagian atas aplikasi.

## Hubungkan

Pasang adaptor lewat OTG, hidupkan kontak, pilih USB lalu tekan **Hubungkan**.
Izinkan akses USB di dialog Android. Tahap koneksi tampil di layar: membuka USB,
wake-up, menunggu ECU, ID dan deteksi sensor.
**USB terdeteksi** hanya menyatakan adaptor ditemukan. **ECU terhubung** baru
ditampilkan setelah diagnostic entry mendapat frame ECU yang valid.
Scan mencoba maksimal tiga kali; ID opsional yang tidak terbaca tidak menghalangi
sensor atau DTC.

Driver: USB **0403:6001**, revisi FT232R **0600**, satu UART, **10400 baud / 8N1**,
tanpa flow control. Adaptor harus memiliki rangkaian K-Line dan pinout yang sesuai.
CH340, CP210x, Bluetooth dan FTDI multikanal belum didukung.
Panel **Detail** dapat dilipat untuk layar pendek/landscape.
Meninggalkan layar atau melepas adaptor menutup komunikasi ECU. Hubungkan kembali
untuk melanjutkan. Hasil izin USB dapat diselesaikan saat kembali dari dialog.

## Live, Grafik dan DTC

- **Live:** seluruh sensor bernama dari tabel ECU, pencarian, kategori, kualitas,
  usia sampel, min/maks/rata-rata dan frekuensi aktual.
- **Jeda/Mulai live**, **Baca sekali** saat dijeda, serta target interval
  250/500/1000/2000 ms. Baca sekali tetap dijeda setelah selesai.
- **Grafik:** pilih sensor numerik; 120 sampel terakhir memakai waktu pembacaan
  sebenarnya. Data kosong memutus garis.
- **Simpan CSV:** maksimal 600 sampel, waktu UTC/epoch, nilai, jumlah grup valid
  dan detail kegagalan. **Reset statistik & riwayat** memulai akumulasi baru.
- **DTC:** kode saat ini dan tersimpan; live dijeda selama operasi dan dilanjutkan
  jika sebelumnya aktif.

Putaran lambat selesai dahulu sebelum putaran berikutnya; I/O tidak tumpang tindih.
Data yang hilang ditandai **Belum terbaca**, tanpa menyalin angka lama.
Saat dijeda/terputus, data ditandai **Sampel terakhir**.
Live berhenti setelah tiga putaran berturut-turut tanpa grup valid.
Ukuran tabel mengikuti balasan ECU. ID katalog ambigu tetap menampilkan jumlah
definisi yang cocok.

RPM, V, °C dan ° memakai skala source referensi. Tekanan, VSS, injektor dan sinyal
lain yang belum memiliki skala terverifikasi tetap raw; kPa/km/h/ms tidak ditebak.
Riwayat sensor berada dalam memori Activity. Simpan CSV sebelum menutup atau
memutar layar. Tab dan putus koneksi mempertahankan riwayat selama Activity hidup.
Pemilih lokasi simpan menutup koneksi; snapshot ekspor sudah diambil sebelumnya.
BIN/XDF, remap, hapus DTC dan pemrograman ECU belum tersedia.

## Debug jika ECU belum terbaca

Biarkan **Detail TX/RX USB & K-Line** aktif di **Debug**, lalu coba **Hubungkan**.
Kegagalan koneksi membuka Debug otomatis. **Salin log** atau **Simpan .txt**
mengekspor trace yang tersimpan tanpa izin storage tambahan.

Log mencatat perangkat/Android, izin, descriptor/endpoint, control FTDI, tahap
scan, TX/RX mentah dan UART, echo, timeout, checksum, tabel, serta stack trace.
Pesan membedakan tidak ada RX, echo tanpa balasan ECU dan balasan terpotong.
Waktu pulsa di log adalah pengukuran host, bukan pengukuran pin fisik.
**Error terakhir** menyimpan snapshot privat yang dapat dibuka setelah aplikasi
dijalankan ulang. **Bersihkan log sekarang** mempertahankan arsip error terakhir.
Trace aktif dibatasi 2.000 entri / sekitar 256 KiB karakter. Layar menampilkan 240
entri terbaru; salin/simpan mencakup trace tersimpan. Logcat: `adb logcat -s HondaTuner`.

Default: **Echo otomatis**, timeout **1000 ms**. Auto menerima echo lengkap lalu
balasan atau balasan ECU langsung; header, panjang dan checksum tetap diperiksa.
Wake/echo saja tidak dianggap ACK. Echo wajib/tanpa echo dan timeout dapat dipilih
untuk diagnosis saat koneksi terputus.

## Build di GitHub

Repo: [FarizFx/honda-tuner-android](https://github.com/FarizFx/honda-tuner-android).
Workflow: [.github/workflows/build-android.yml](.github/workflows/build-android.yml).
Push source memicu build. Manual: [Actions](https://github.com/FarizFx/honda-tuner-android/actions)
→ **Build Android APK → Run workflow → main**. README saja tidak memicu build.

Runner default `self-hosted` harus online. Build pengguna memakai server Linux
**KC (runner ID 27)**. Laptop tidak didaftarkan sebagai runner atau dipakai untuk APK ini.
Workflow memakai Python 3.12/PyYAML, Java 17, Gradle 8.9, AGP 8.7.3, SDK 35 dan
build-tools 35.0.0; menjalankan verifikasi source/core, `:app:assembleDebug :app:lintDebug`,
kemudian memeriksa signature, versi/manifest dan ZIP.
Step **Report Android lint findings** selalu menampilkan semua error/fatal dari
laporan lint XML. Error lint tetap menggagalkan build dan pengiriman APK.

Hasil main menjadi **draft prerelease** untuk pemilik repo, berisi APK
`HondaTuner-Android-debug.apk`, sidecar SHA-256, `apk_verification.json`,
`core_verification.json`, `core_tests.txt`, `project_verification.json` dan
`reference_provenance.json`. Bandingkan hash APK dengan sidecar/laporan.
APK memakai sertifikat debug runner. Actions artifacts opsional melalui input
`upload_actions_artifacts` jika kuota tersedia. `dist/` dan toolchain tidak masuk Git.

## Tes dan build mandiri

Tes core membutuhkan JDK yang sudah tersedia, tanpa SDK/ECU:

```sh
python -m pip install PyYAML==6.0.2
python tools/verify_project.py
python tools/test_core.py --java-home /path/to/jdk
```

Suite mencakup framing/echo, retry, FTDI status, DTC, live/cancel/session, logging,
riwayat dan CSV. Fixture mencakup 13 varian tabel; input terkontrol, bukan rekaman
kendaraan. `tools/sync_reference.py` memperbarui ekspor referensi bila source
desktop tersedia; CI memakai ekspor yang disertakan.
Build mandiri membutuhkan JDK 17, Gradle 8.9 dan SDK 35; tentukan `ANDROID_HOME`
atau `sdk.dir` dalam `local.properties`:

```sh
gradle --no-daemon --console=plain :app:assembleDebug :app:lintDebug
python tools/package_apk.py --apk app/build/outputs/apk/debug/app-debug.apk --sdk /path/to/android-sdk --java-home /path/to/jdk-17
```

Repo belum menyertakan Gradle Wrapper.
Status: [evidence/build_status.json](evidence/build_status.json).
APK terbaru dibangun dari source `9913287bb961ceb45131950b7ef0c437805d2da6`.
Baseline 1.0.0 dibangun pada
[run 43683216038](https://github.com/FarizFx/honda-tuner-android/actions/runs/43683216038).
Build dan tes source tidak membuktikan keberhasilan ECU fisik pada APK pembaruan.
