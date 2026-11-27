# Scan Nota

Foto nota -> teks dibaca di HP (ML Kit) -> form terisi -> koreksi -> simpan -> bagikan CSV.

## Cara menjalankan
1. Install Android Studio di laptop.
2. Ekstrak ZIP, lalu di Android Studio pilih **Open** dan pilih folder `ScanNota`.
3. Tunggu "Gradle Sync" selesai (butuh internet, pertama kali agak lama).
   Kalau diminta install SDK Android 34, setujui. Kalau ditawari upgrade plugin, lewati dulu.
4. Di HP: Pengaturan > Tentang ponsel > ketuk "Nomor build" 7x,
   lalu aktifkan **USB debugging** di Opsi pengembang.
5. Colok HP ke laptop, klik tombol Run.

File APK: menu Build > Build APK(s). Hasilnya di `app/build/outputs/apk/debug/`.

## Catatan
- Hasil scan adalah tebakan, selalu cek sebelum simpan. Foto yang terang dan lurus lebih akurat.
- Aturan pembacaan ada di `ReceiptParser.kt`.
- Data tersimpan di dalam aplikasi dan hilang kalau aplikasi di-uninstall. Ekspor CSV untuk cadangan.
- CSV dipisah koma. Kalau Excel menumpuk semua di satu kolom, pakai Data > From Text/CSV.

## Build lewat HP (GitHub Actions)
Setiap perubahan di branch `main` otomatis di-build. Buka tab **Actions**, pilih hasil build yang
bercentang hijau, lalu download **scan-nota-apk** di bagian Artifacts. Ekstrak dan install APK-nya.

APK debug dari build berbeda memakai tanda tangan berbeda, jadi versi lama harus di-uninstall dulu.
Ekspor CSV sebelum uninstall supaya data tidak hilang.
