# Honda Tuner Android

Diagnostik ECU Honda lewat adaptor FT232R/FT232RL K-Line dan USB OTG.
Minimum Android 8.0 (API 26) dengan USB host.

**v1.1.7-debug / versionCode 9 sudah dibangun dan terverifikasi.**
[Build Actions 1.1.7](https://github.com/FarizFx/honda-tuner-android/actions/runs/43816932505) berhasil pada Linux
6 / ID33. APK: `dist/HondaTuner-1.1.7-debug.apk`.
Simpan CSV/log yang diperlukan, uninstall 1.1.6, lalu pasang 1.1.7. Sertifikat debug berbeda; uninstall menghapus data privat dan pengaturan aplikasi.
Laporan CI yang disertakan merupakan hasil asli build 1.1.7. APK, source, dan
laporan 1.1.6 serta versi sebelumnya tetap disimpan dalam baseline/arsip.

**Edit tata letak** membuka kanvas HUD. Pilih kotak lalu seret untuk memindahkan;
tarik salah satu dari empat titik sudut untuk mengubah lebar dan tinggi.
Kotak utama, tiga sensor pendamping, bar RPM, judul, status koneksi, refresh,
dan info VSS dapat diatur secara terpisah. Kotak boleh bertumpuk; **⋯ Kotak →
Pilih kotak** menjangkau elemen yang tertutup dan membawanya ke depan.
Menu yang sama dapat mengganti sensor pada kotak yang dipilih, mereset satu
kotak, atau mereset tata letak orientasi yang sedang dibuka.

**Simpan** menyimpan posisi, ukuran, lapisan, dan perubahan sensor dari editor.
**Batal** atau Back HP mengembalikan tata letak dan sensor sebelum editor dibuka.
Posisi portrait dan landscape disimpan terpisah serta mengikuti ukuran kanvas.
Editor memakai tampilan tegak; cermin HUD kembali berlaku setelah selesai.
Pilihan sensor/warna di form Atur HUD diterapkan saat tombol Edit tata letak
membuka editor; Batal di editor membatalkan perubahan selama di kanvas.

Mode speedometer memakai layar penuh. Ketuk layar untuk memunculkan kontrol
Live, Jeda/Mulai, Atur HUD, dan Edit tata letak; kontrol melayang dan otomatis
hilang setelah empat detik. Kotak tetap diam ketika menu muncul. Back saat
melihat speedometer kembali ke Live tanpa memutus USB. Tata letak tetap dapat
diedit saat belum terhubung; nilai yang belum terbaca tetap berupa tanda pisah.

**Speedometer LCD** membuka layar instrumen dengan angka segmen, bar RPM, dan
tiga sensor pendamping. **Atur HUD** menyimpan pilihan sensor, warna, ukuran
angka, skala bar RPM, landscape, cermin untuk HUD, dan fokus pembacaan.
Tombol **Mode miring / Mode tegak** juga tersedia pada Live. Rotasi layar
mempertahankan koneksi USB, sampel, dan statistik selama Activity tetap hidup.

**Min / Avg / Max** di bawah nilai sensor menghitung seluruh sampel numerik
valid sepanjang sesi, termasuk nilai 0; batas grafik/CSV 600 sampel tidak
membatasi statistik. Sampel kosong, boolean, dan nonfinite tidak ikut dihitung.
Putus koneksi mereset statistik. Jeda, pergantian tampilan, urutan sensor,
dan deteksi ulang dalam sesi yang sama mempertahankannya. `n=` dihapus.

Target refresh ditampilkan sebagai **0/50/100/250/500/1000/2000 ms**.
**Refresh aktual** mengukur selisih waktu monotonic antarpenyelesaian putaran
dalam ms; rata-rata memakai delapan interval terbaru. Durasi putaran dan
interval terakhir juga ditampilkan. Target 0 ms tidak menambah jeda penjadwalan;
durasi aktual mengikuti jumlah grup, balasan ECU, dan retry. Jeda dan deteksi
ulang tidak ikut dihitung sebagai interval live.

**Baca hanya grup sensor pilihan** dan fokus HUD mengurangi transaksi.
Sensor satu grup tetap dibaca bersama; grup yang tidak dipilih menjadi belum
terbaca di grafik/CSV. Kembali dari LCD memulihkan pilihan pembacaan Live.
Pencarian dan kategori menyaring tampilan. Pilihan sensor dan interval tersimpan.
Grafik RPM, TPS, tegangan, dan VSS dimulai dari 0 dengan label nol yang terlihat.
Suhu dan pengapian mempertahankan nilai negatif yang memang terbaca.

**Speed / VSS** tersedia hanya jika tabel ECU menyediakannya. Skala km/h belum
terverifikasi, sehingga VSS memakai **RAW**. Tabel 0x17 pada rekaman pengguna
tidak memuat VSS; LCD memakai RPM secara default ketika VSS tidak tersedia.
Nilai sensor yang belum terbaca ditampilkan sebagai tanda pisah.

Log terbaru **1.1.5** melaporkan **Status RX FT232R tidak tersedia (-1)**.
ID **0102450F01** sudah terbaca; empat grup / 29 sensor menghasilkan satu
putaran lengkap **220,5 ms**. Cuplikan memuat 219 paket status tanpa data dan
berhenti di tengah entri 84883 sebelum pemeriksaan yang gagal. Penyebab fisik
hasil kontrol -1 belum diketahui, tetapi source sebelumnya langsung memutus
sesi pada satu hasil tersebut.

Pada 1.1.6, paket FTDI yang valid atau status TX yang baru berhasil diterima
menjadi bukti koneksi USB selama 100 ms; pemeriksaan RX tambahan tidak dikirim
selama bukti itu masih baru. Hasil kontrol **-1** yang tersendiri memberi
kesempatan membaca USB lagi. Tiga pemeriksaan gagal berturut-turut, berjarak
minimal 50 ms dan tanpa bukti USB baru, tetap memutus sesi. Status pendek atau
tidak valid, exception USB, TX yang tidak pasti, UART ECU, overrun, target,
panjang, checksum, echo ambigu, dan jendela tenang 24 ms tetap diperiksa.
Probe memakai sisa anggaran timeout baca. Timer latensi FTDI tetap 1 ms.
Paket status tanpa data diringkas dengan jumlah dan flag terakhir; seluruh
paket yang membawa data UART tetap dicatat utuh. **Salin akhir log** menjaga
header versi/error dan entri terakhir, sementara **Simpan .txt** tetap lengkap.
Perbaikan GET_STATUS dan parser ID dari 1.1.5 juga tetap disertakan; kedua
kecocokan Vario 150 dalam katalog tidak dihilangkan.

**243 tes regresi JVM**, **312 vektor referensi / 5.616 nilai**, dan
**16 vektor rekaman / 166 nilai** lulus pada server untuk source core 1.1.7.
Tes baru mencakup batas gerak, empat sudut resize, penyimpanan lapisan,
orientasi terpisah, layout rusak, slot tersembunyi, dan gambar kotak yang di-resize.
Kompilasi SDK, lint (0 error / 35 warning), signature, ZIP, ketujuh digest aset,
kelas editor/HUD/USB, katalog, dan 58 hash source terverifikasi.
Pengujian sentuhan langsung pada HP/ECU 1.1.7 masih memerlukan retest pengguna.
Baseline 1.1.5 dan versi sebelumnya dipertahankan. Bukti pembaruan:
[evidence/dashboard_1_1_6.json](evidence/dashboard_1_1_6.json).
Audit perbaikan sebelumnya: [evidence/phone_1_1_4_audit.json](evidence/phone_1_1_4_audit.json).
Pengujian HP/ECU: [PHYSICAL_TEST.md](PHYSICAL_TEST.md).

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
  usia sampel, Min / Avg / Max dan refresh aktual dalam ms.
- **Jeda/Mulai live**, **Baca sekali** saat dijeda, serta target interval
  0/50/100/250/500/1000/2000 ms. Baca sekali tetap dijeda setelah selesai.
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
Riwayat sensor berada dalam memori Activity. Simpan CSV sebelum menutup aplikasi.
Rotasi dan tab mempertahankan riwayat serta statistik. Putus koneksi mereset
statistik dan mempertahankan sampel grafik/CSV sampai sesi baru dimulai.
Pemilih lokasi simpan menutup koneksi; snapshot ekspor sudah diambil sebelumnya.
BIN/XDF, remap, hapus DTC dan pemrograman ECU belum tersedia.

## Debug jika ECU belum terbaca

Biarkan **Detail TX/RX USB & K-Line** aktif di **Debug**, lalu coba **Hubungkan**.
Kegagalan koneksi membuka Debug otomatis. **Salin akhir log** mengambil header
versi/error dan maksimal 120 entri terakhir / 32 KiB karakter. **Simpan .txt**
mengekspor seluruh trace yang tersimpan tanpa izin storage tambahan.

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
entri terbaru; salin mengambil bagian akhir dan simpan mencakup trace tersimpan.
Logcat: `adb logcat -s HondaTuner`.

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

Runner default `self-hosted` harus online. **Server Linux 4 (ID 31)** membangun
1.1.4 dan 1.1.5. Seluruh runner terdaftar sedang offline saat kelanjutan 1.1.6;
build baru ditahan. Registrasi runner dipertahankan. Laptop tidak dipakai untuk APK.
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
identitas, logging, statistik sepanjang sesi, refresh monotonic, skala grafik,
layout LCD portrait/landscape, fokus HUD, riwayat dan CSV. Vektor referensi mencakup 13 varian tabel dengan
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
Status source 1.1.6: [evidence/dashboard_1_1_6.json](evidence/dashboard_1_1_6.json).
Build terakhir yang selesai: [evidence/build_status.json](evidence/build_status.json),
source 1.1.5 `443d36cc52ca282faeaadaebb2264682bb6602d1`, run 43793080151.
Source 1.1.6 belum dipush dan belum memiliki job Actions atau APK.
Baseline 1.0.0 dibangun pada
[run 43683216038](https://github.com/FarizFx/honda-tuner-android/actions/runs/43683216038).
Build dan tes source tidak membuktikan keberhasilan ECU fisik pada APK pembaruan.
