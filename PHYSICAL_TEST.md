# Pengujian Honda Tuner 1.1.4 pada HP dan ECU

Gunakan Xiaomi/FT232RL/ECU Shindengen dan rangkaian yang sama dengan pengujian
1.1.3. Rekaman pengguna memuat frame sensor ECU yang valid sesudah echo dengan
error UART. Replay 1.1.4 menerima frame yang bersih tersebut; respons sensor
terhadap perubahan fisik dan koneksi awal langsung masih perlu diuji.

1. Pasang APK 1.1.4 yang telah diperiksa. Ikuti catatan kompatibilitas pemasangan
   pada hasil build. Sambungkan adaptor langsung lewat OTG, dengan kontak
   seperti pengujian sebelumnya. Koneksi ini tidak dibantu HondaECU di PC.
2. Pilih Echo AUTO, timeout 1000 ms dan aktifkan Detail TX/RX. Tekan Hubungkan.
   Hasil harus mencapai ECU terhubung lalu live; USB terdeteksi saja belum cukup.
   Identitas kosong atau tidak ada dalam katalog boleh tetap terhubung.
   Salin log segera setelah koneksi; header Identitas/Model/Katalog harus memuat
   hasil ID dan jumlah definisi. Jika ID kosong, periksa penyebabnya di header.
3. Putuskan lalu hubungkan kembali dua kali. Koneksi lama harus tertutup dan
   callback lama tidak boleh mengubah koneksi baru.
4. Biarkan live berjalan minimal 20 putaran. Periksa waktu sampel terus berubah,
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
