# Honda Tuner Android

Diagnostik ECU Honda lewat adaptor FT232R/FT232RL K-Line dan USB OTG.
Minimum Android 8.0 (API 26) dengan USB host.

**v1.1.5-debug / versionCode 7:** Live menyediakan **Pilih & urutkan sensor**.
Centang sensor yang ingin ditampilkan, ubah urutan dengan tombol naik/turun,
lalu Simpan. Pilihan bertahan setelah aplikasi dibuka ulang dan saat tabel ECU
berubah; sensor baru tetap tampil. Mengatur tampilan tidak mereset riwayat.

Target **Secepatnya** tidak menambah jeda setelah satu putaran pembacaan selesai.
Pilihan lain: 50/100/Secepatnya atau 50/100/250/500/1000/2000 ms. Waktu aktual mengikuti balasan ECU dan
jumlah grup, tetap tampil sebagai Hz aktual. **Baca hanya grup sensor pilihan**
mengurangi jumlah transaksi; sensor satu grup tetap dibaca bersama, sedangkan
grup yang tidak dipilih menjadi belum terbaca di grafik/CSV. Pencarian dan
kategori hanya menyaring tampilan. Interval yang tersimpan dari versi lama
dipertahankan; pemasangan baru memakai Secepatnya.

Timer latensi FT232R kini 1 ms. Pemeriksaan koneksi USB pada pembacaan kosong
yang kembali terlalu cepat memakai status vendor FTDI, menggantikan USB
GET_STATUS generik yang gagal pada laporan 1.1.4. Status pendek/gagal serta
kesalahan USB/TX tetap menghentikan sesi. Pemeriksaan target, panjang, checksum,
UART ECU, overrun, echo ambigu, dan jendela tenang frame 24 ms tetap berlaku.

Pada log pengguna, **1.1.4 berhasil terhubung dan membaca 29 sensor dalam empat
grup**, dengan tiga putaran lengkap sekitar 235–259 ms. Satu balasan utama
terpotong menjadi 22/24 byte, lalu dipulihkan oleh retry. Log berhenti di tengah
baris sebelum transaksi USB yang gagal, sehingga penyebab fisik error kontrol
USB belum diketahui. Header ID `72 00 00` berisi **0102450F01** kini diterima
bersama varian `72 00 05`, dengan syarat frame lengkap 11 byte dan checksum valid.
Katalog berisi **245 definisi / 244 ID unik**; ID tersebut cocok dengan dua
Vario 150 (K59F-A01 dan K59A-A11), dan keduanya tetap ditampilkan.

**200 tes regresi lokal**, **312 vektor referensi / 5.616 nilai**, dan
**16 vektor rekaman / 166 nilai** lulus. [Build Actions 1.1.5](https://github.com/FarizFx/honda-tuner-android/actions/runs/43793080151)
berhasil pada server Linux 4. Kompilasi SDK, signature dan ZIP APK lulus;
lint: 0 error / 36 warning. APK: `dist/HondaTuner-1.1.5-debug.apk`.
Pasang 1.1.5 sebagai pembaruan 1.1.4; sertifikat debug sama.
APK/laporan 1.1.4
dipertahankan di `dist/baseline-1.1.4`; semua versi sebelumnya juga dipertahankan.
Bukti: [evidence/phone_1_1_4_audit.json](evidence/phone_1_1_4_audit.json).
Perbandingan transport sebelumnya: [evidence/lite_transport_reference.json](evidence/lite_transport_reference.json).
Pengujian HP/ECU: [PHYSICAL_TEST.md](PHYSICAL_TEST.md). Hasil versi baru pada
perangkat dan latensi aktual masih perlu diuji.

## Hubungkan

Pasang adaptor lewat OTG, hidupkan kontak, pilih USB lalu tekan **Hubungkan**.
Izinkan akses USB di dialog Android. Tahap koneksi tampil di layar: membuka USB,
wake-up, menunggu ECU, ID dan deteksi sensor.
**USB terdeteksi** hanya menyatakan adaptor ditemukan. **ECU terhubung** baru
ditampilkan setelah diagnostic entry mendapat frame ECU yang valid.
Scan mencoba maksimal tiga kali; ID opsional yang tidak terbaca tidak menghalangi
sensor atau DTC.

Scan menggunakan diagnostik Honda untuk Keihin dan Shindengen, tanpa pilihan
vendor atau syarat ID harus masuk katalog. Sensor dipilih dari tabel balasan ECU.
Katalog nama dan ID bukan bukti bahwa setiap model ECU fisik sudah diuji; K-Line
sendiri tidak menjamin semua kendaraan memakai protokol diagnostik Honda ini.

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
  Secepatnya atau 50/100/250/500/1000/2000 ms. Baca sekali tetap dijeda setelah selesai.
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
Log driver mencatat perintah BREAK dan log modem menunjukkan TX kosong.
Durasi pulsa diukur host; level/timing pada pin K-Line ECU belum diukur.
Error UART mencantumkan rentang byte RX dan apakah hanya mengenai echo.
Header Debug mempertahankan hasil pembacaan ID, model dan jumlah entri katalog.
**Error terakhir** menyimpan snapshot privat yang dapat dibuka setelah aplikasi
dijalankan ulang. **Bersihkan log sekarang** mempertahankan arsip error terakhir.
Trace aktif dibatasi 2.000 entri / sekitar 256 KiB karakter. Layar menampilkan 240
entri terbaru; salin/simpan mencakup trace tersimpan. Logcat: `adb logcat -s HondaTuner`.

Default: **Echo otomatis**, timeout **1000 ms**. Auto menerima echo lengkap lalu
balasan atau balasan ECU langsung; header, panjang dan checksum tetap diperiksa.
Wake memakai drain RX hingga 60 ms setelah UART selesai mengirim. RX wake opsional
dikuras; balasan ECU selanjutnya harus bersih. Pada AUTO, error echo diterima hanya
di prefix sepanjang TX yang diikuti frame ECU valid. Error transport USB tetap
menggagalkan koneksi. Wake/echo saja tidak dianggap ACK. Echo wajib/tanpa echo dan timeout dapat dipilih
untuk diagnosis saat koneksi terputus.

## Build di GitHub

Repo: [FarizFx/honda-tuner-android](https://github.com/FarizFx/honda-tuner-android).
Workflow: [.github/workflows/build-android.yml](.github/workflows/build-android.yml).
Push source memicu build. Manual: [Actions](https://github.com/FarizFx/honda-tuner-android/actions)
→ **Build Android APK → Run workflow → main**. README saja tidak memicu build.

Runner default `self-hosted` harus online. **Server Linux 4 (ID 31)**
membangun 1.1.4 dan masih online saat source 1.1.5 disiapkan. Registrasi runner
dipertahankan. Laptop tidak dipakai untuk APK.
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

Suite mencakup framing/echo, retry, FTDI BREAK/FIFO/drain/status, DTC, live/cancel/session,
identitas, logging, riwayat dan CSV. Vektor referensi mencakup 13 varian tabel dengan
input terkontrol. Fixture telepon memuat 17 transaksi rekaman USB; replay tidak
melakukan I/O pada kendaraan. `--output-dir PATH` menyimpan laporan tes lokal
terpisah dari bukti CI. `tools/sync_reference.py` memperbarui ekspor referensi bila source
desktop tersedia; CI memakai ekspor yang disertakan.
Build mandiri membutuhkan JDK 17, Gradle 8.9 dan SDK 35; tentukan `ANDROID_HOME`
atau `sdk.dir` dalam `local.properties`:

```sh
gradle --no-daemon --console=plain :app:assembleDebug :app:lintDebug
python tools/package_apk.py --apk app/build/outputs/apk/debug/app-debug.apk --sdk /path/to/android-sdk --java-home /path/to/jdk-17
```

Repo belum menyertakan Gradle Wrapper.
Status: [evidence/build_status.json](evidence/build_status.json).
Source 1.1.4: `7ae1d6b07e8281ee1d163b61e4c7de74c809574d`. Build dan APK belum
terverifikasi karena job masih mengantre untuk runner Linux yang offline.
Baseline 1.0.0 dibangun pada
[run 43683216038](https://github.com/FarizFx/honda-tuner-android/actions/runs/43683216038).
Build dan tes source tidak membuktikan keberhasilan ECU fisik pada APK pembaruan.
