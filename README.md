# Scan Nota

Foto nota -> teks dibaca di HP (ML Kit) -> form terisi -> koreksi -> simpan -> riwayat dan rekap.

## Cara menjalankan
1. Install Android Studio di laptop.
2. Ekstrak ZIP, lalu di Android Studio pilih **Open** dan pilih folder `ScanNota`.
3. Tunggu "Gradle Sync" selesai (butuh internet, pertama kali agak lama).
   Kalau diminta install SDK Android 34, setujui. Kalau ditawari upgrade plugin, lewati dulu.
4. Di HP: Pengaturan > Tentang ponsel > ketuk "Nomor build" 7x,
   lalu aktifkan **USB debugging** di Opsi pengembang.
5. Colok HP ke laptop, klik tombol Run.

File APK: menu Build > Build APK(s). Hasilnya di `app/build/outputs/apk/debug/`.

## Fitur
- Login demo: nomor HP + PIN, tersimpan di HP (tanpa server). Data dipisah per akun.
- Scan nota dengan pemindai dokumen (potong dan luruskan otomatis), kamera, galeri, atau isi manual
- Foto nota tersimpan, barang per baris, peringatan bila total beda atau nota ganda
- Beranda, rekap bulanan, belanja per toko, ekspor Excel (.xlsx)

## Catatan
- Hasil scan adalah tebakan, selalu cek sebelum simpan.
- Aturan pembacaan ada di `ReceiptParser.kt` dan `RowGrouper.kt`.
- Pemindai dokumen butuh Google Play Services dan internet saat pertama dipakai.
- Belum ada fitur lupa PIN. Data hilang kalau aplikasi di-uninstall.
- Font Inter (SIL Open Font License 1.1), https://github.com/rsms/inter

## Build lewat HP (GitHub Actions)
Setiap perubahan di branch `main` otomatis di-build. Buka tab **Actions**, pilih hasil build yang
bercentang hijau, lalu download **scan-nota-apk** di bagian Artifacts. Ekstrak dan install APK-nya.

APK debug dari build berbeda memakai tanda tangan berbeda, jadi versi lama harus di-uninstall dulu.
Ekspor CSV sebelum uninstall supaya data tidak hilang.
