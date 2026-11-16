# Pengujian Honda Tuner 1.1.6 pada HP dan ECU

Gunakan Xiaomi/FT232RL/ECU Shindengen dan rangkaian yang sama dengan pengujian
1.1.4. Rekaman pengguna memuat frame sensor ECU yang valid sesudah echo dengan
error UART. Log terbaru 1.1.5 menunjukkan ID ECU terbaca dan 29 sensor dalam
empat grup berjalan, lalu melaporkan Status RX FT232R tidak tersedia (-1).
Replay frame D0 terbaru dan skenario pemeriksaan status yang gagal telah lulus.
APK 1.1.6 belum dibangun; lakukan pengujian berikut setelah APK server tersedia.

1. Pasang APK 1.1.6 yang telah diperiksa. Ikuti catatan kompatibilitas pemasangan
   pada hasil build. Sambungkan adaptor langsung lewat OTG, dengan kontak
   seperti pengujian sebelumnya. Koneksi ini tidak dibantu HondaECU di PC.
2. Pilih Echo AUTO, timeout 1000 ms dan aktifkan Detail TX/RX. Tekan Hubungkan.
   Hasil harus mencapai ECU terhubung lalu live; USB terdeteksi saja belum cukup.
   Identitas kosong atau tidak ada dalam katalog boleh tetap terhubung.
   Salin log segera setelah koneksi; header Identitas/Model/Katalog harus memuat
   hasil ID dan jumlah definisi. Jika ID kosong, periksa penyebabnya di header.
3. Putuskan lalu hubungkan kembali dua kali. Koneksi lama harus tertutup dan
   callback lama tidak boleh mengubah koneksi baru.
4. Biarkan live berjalan minimal lima menit, melewati durasi sekitar 85 detik
   pada cuplikan sebelum laporan error 1.1.5. Periksa waktu sampel terus berubah,
   grup sensor valid, TPS/aki/ECT muncul, dan nilainya merespons perubahan nyata. Simpan CSV agar
   jumlah putaran serta nilai sensor dapat diperiksa kembali.
5. Jeda, Baca sekali, kemudian mulai live kembali. Baca DTC saat live berjalan;
   periksa hasil saat ini dan tersimpan, lalu live kembali berjalan. Hasil DTC
   kosong dianggap berhasil hanya jika kedua grup mendapat balasan valid.
6. Simpan log lengkap dan CSV. Jika gagal, catatan TRANSACTION harus memuat TX,
   RX, kualitas echo, status/rentang error UART, waktu tunggu dan penyebab pertama. Kirim
   hasil koneksi/reconnect/live/DTC beserta log, tanpa menyimpulkan byte RX
   yang rusak sebagai kode kerusakan motor.

Status pengujian fisik tetap belum terverifikasi sampai langkah tersebut
dibuktikan pada perangkat. Data yang datang setelah timeout atau setelah
jendela tenang tidak dapat diprediksi oleh parser dari rekaman sebelumnya.

7. Pilih beberapa sensor dan ubah urutan, simpan, lalu buka ulang aplikasi.
   Periksa pilihan dan urutan tetap tersimpan tanpa mereset riwayat saat diedit.
8. Pilih target 0 ms. Bandingkan refresh aktual dalam ms dengan seluruh grup
   dan hanya sensor utama (RPM/TPS/aki). Catat durasi putaran dan interval
   terakhir; target 0 ms tetap memerlukan waktu komunikasi ECU. Periksa grup yang tidak dibaca menjadi belum terbaca
   di grafik/CSV. Kembali ke semua grup dan periksa nilai kembali diperbarui.
9. Periksa ID 0102450F01 dan kedua entri Vario 150 bila ECU yang sama digunakan.
   Biarkan pembacaan kosong/timeout terjadi, lalu periksa apakah live tetap
   berjalan. Lepas USB dan pastikan sesi terputus; hubungkan kembali langsung.

10. Buka Speedometer LCD. Pilih angka utama dan sensor pendamping, warna,
    ukuran angka, skala bar RPM, dan cermin HUD. Simpan, kembali ke Live, dan
    buka ulang aplikasi; pilihan HUD harus tetap tersimpan. Tombol Live dan
    Atur HUD harus tetap dapat digunakan ketika display dicerminkan.
11. Putar layar saat live berjalan; gunakan Mode miring / Mode tegak serta
    landscape otomatis pada LCD. Koneksi USB dan rekor Min / Avg / Max harus
    tetap berjalan. Coba LCD dengan ukuran font dan display Android yang dipakai.
12. Bandingkan Min / Avg / Max dengan seluruh sampel valid satu sesi. Sertakan
    TPS 0 dan bukaan maksimum. Setelah lebih dari 600 sampel, rekor awal harus
    tetap ikut dihitung. Jeda, ubah urutan sensor, dan deteksi ulang; statistik
    tidak boleh direset. Putus koneksi harus segera mengosongkan rekor.
13. Periksa grafik RPM/TPS/aki saat 0 dan idle: label 0 terlihat dan batas bawah
    tidak menjadi negatif karena padding. Suhu/pengapian negatif yang valid
    tetap harus terlihat. Grup gagal atau tidak dipilih memutus garis grafik.
14. Bandingkan refresh sebelum dan sesudah fokus HUD. Jeda dan deteksi ulang
    tidak boleh menambah interval scan ke angka refresh live. Kembali ke Live
    harus memulihkan pilihan grup Live, dengan nilai baru pada grup yang dibaca.
15. Speed/VSS hanya boleh muncul sebagai nilai nyata jika schema ECU mendukungnya;
    unit tetap RAW sampai skala km/h terverifikasi. Pada tabel 0x17 rekaman ini,
    VSS belum tersedia dan angka utama default adalah RPM.

Jika error masih muncul pada APK terbaru, sertakan header versi APK dan 80–120
baris sekitar error paling akhir, terutama USB SESSION, stack trace, FTDI control,
dan TRANSACTION. Gunakan Error terakhir → Salin akhir log untuk bagian akhir
beserta header versi dan stack trace, atau Simpan .txt untuk trace lengkap.
Cuplikan 1.1.5 yang diterima berhenti di tengah entri 84883 sebelum pemeriksaan
status yang gagal; penyebab fisik hasil -1 belum dibuktikan oleh cuplikan tersebut.
