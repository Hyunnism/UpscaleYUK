# UpscaleYUK

UpscaleYUK adalah aplikasi Android untuk meningkatkan resolusi foto (super-resolution) secara lokal langsung di perangkat menggunakan model kecerdasan buatan Real-ESRGAN yang dioptimalkan dengan framework Tencent NCNN dan akselerasi GPU Vulkan. Seluruh proses inferensi berjalan sepenuhnya secara offline tanpa mengirim data ke server luar, menjaga privasi pengguna dan efisiensi kuota data.

---

## Fitur Utama

- Pemrosesan Lokal Penuh (Offline): Inferensi AI berjalan langsung pada GPU perangkat menggunakan Vulkan API. Tidak memerlukan koneksi internet maupun akun pengguna.
- Dukungan Multi-Skala: Menyediakan opsi pembesaran 2x, 4x, dan 8x dengan sistem pemotongan ubin (tiling) dinamis untuk menjaga konsumsi memori GPU tetap stabil.
- Pembanding Visual Interaktif (Before/After Slider): Komponen kanvas dengan pembatas vertikal yang dapat digeser untuk membandingkan foto asli dan hasil upscale secara langsung pada rasio 1:1, dilengkapi dukungan gestur zoom (pinch-to-zoom) dan geser (pan) tersinkronisasi.
- Penyimpanan Galeri Otomatis: Menyimpan hasil render format PNG tanpa penurunan kualitas (lossless) langsung ke direktori Pictures/UpscaleYUK menggunakan MediaStore API modern Android.
- Normalisasi Orientasi EXIF: Membaca metadata foto kamera secara otomatis dan memutar gambar ke posisi tegak sebelum proses AI berjalan.
- Proteksi Layar Aktif: Mengaktifkan flag penahan layar otomatis selama proses inferensi berlangsung agar layar ponsel tidak mati atau terkunci di tengah proses rendering.
- Antarmuka Tema Gelap: Desain berbasis Material 3 bernuansa gelap dengan implementasi edge-to-edge yang menyatu dengan status bar dan bar navigasi sistem.

---

## Arsitektur Teknis

Proyek ini dibangun dengan pendekatan kode minimalis (four-core files) untuk mempermudah pemeliharaan dan menghindari dependensi pihak ketiga yang tidak esensial:

1. CMakeLists.txt (C++ Native Build):
   Mengatur konfigurasi NDK r28, kompilasi C++17, dan penautan pustaka statis Tencent NCNN beserta loader Vulkan untuk arsitektur target arm64-v8a.
2. realesrgan_jni.cpp (JNI Bridge & Native Engine):
   Menangani alokasi buffer piksel Android Bitmap, pengelolaan memori GPU NCNN, pembagian gambar menjadi sub-ubin (tiling) dengan padding overlap untuk menghindari garis jahitan (seam artifacts), serta pelaporan progress inferensi secara berulang ke layer Kotlin.
3. UpscaleEngine.kt (Kotlin Engine Wrapper):
   Mengelola siklus hidup mesin native di latar belakang, penyalinan bobot model (bin/param) dari aset APK ke penyimpanan internal aplikasi, serta eksekusi coroutine berbasis asynchronous.
4. MainActivity.kt (UI & Interaksi):
   Komponen tunggal berbasis Jetpack Compose yang mengelola alur kerja pemilihan foto (Photo Picker), pemilihan rasio perbesaran, kanvas pembanding before/after, pelaporan status waktu proses dengan angka tabular, proteksi layar (FLAG_KEEP_SCREEN_ON), dan penyimpanan berkas ke MediaStore.

---

## Persyaratan Sistem

- Sistem Operasi: Android 7.0 (API level 24) ke atas.
- Arsitektur Prosesor: arm64-v8a (64-bit).
- Hardware GPU: Mendukung Vulkan API (Vulkan 1.0 ke atas).
- Versi JDK: Java Development Kit 17.
- Android NDK: Versi 28.2.13676358 atau yang kompatibel.
- Android SDK Platform: SDK 35 (Android 15) dengan Target SDK 35.

---

## Struktur Direktori

```text
Aplikasi UpscaleYUK/
|-- docs/
|   |-- DESIGN.md               # Spesifikasi desain, palet warna, dan aturan layout
|   |-- WORKFLOW.md             # Standar prosedur pengembangan dan pengujian
|   `-- design/evidence/        # Dokumentasi visual dan bukti pengujian setiap fase
|-- UpscaleYUK/
|   |-- app/
|   |   |-- src/main/
|   |   |   |-- assets/         # Bobot model AI (*.bin, *.param)
|   |   |   |-- cpp/            # Kode sumber C++ NDK dan konfigurasi CMake
|   |   |   |-- java/           # Kode sumber Kotlin (UpscaleEngine, MainActivity)
|   |   |   |-- jniLibs/        # Pustaka NCNN arm64-v8a
|   |   |   `-- AndroidManifest.xml
|   |   `-- build.gradle.kts
|   `-- gradlew
|-- UpscaleYuk_Blueprints.md    # Log pengujian 10 fase pengembangan aplikasi
`-- README.md
```

---

## Panduan Kompilasi dan Pemasangan

### 1. Persiapan Awal
Pastikan Android SDK, NDK, dan JDK 17 telah terkonfigurasi pada environment variabel sistem Anda (`ANDROID_HOME` dan `JAVA_HOME`). Sambungkan perangkat Android fisik dengan mode USB Debugging aktif.

### 2. Kompilasi Versi Debug
Untuk mengompilasi dan langsung memasang versi pengujian ke perangkat:

```bash
cd UpscaleYUK
./gradlew installDebug
```

### 3. Kompilasi Versi Rilis (Release)
Untuk menghasilkan berkas APK rilis yang siap digunakan:

```bash
cd UpscaleYUK
./gradlew assembleRelease
```

Berkas APK akan dihasilkan pada lokasi:
`UpscaleYUK/app/build/outputs/apk/release/app-release.apk`

Untuk memasang berkas rilis ke perangkat via ADB:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## Alur Penggunaan Aplikasi

1. Buka aplikasi UpscaleYUK di perangkat.
2. Tekan tombol "Pilih Foto" untuk memilih gambar dari galeri perangkat.
3. Pilih rasio perbesaran yang diinginkan: 2x, 4x, atau 8x. Aplikasi akan menampilkan estimasi dimensi piksel akhir.
4. Tekan tombol "Mulai Upscale". Layar perangkat akan tetap menyala secara otomatis selama GPU memproses gambar, disertai indikator persentase proses real-time.
5. Setelah pemrosesan selesai, durasi eksekusi akan ditampilkan pada panel kontrol.
6. Geser garis pembatas vertikal ke kiri atau kanan untuk membandingkan kualitas foto asli dan hasil AI. Lakukan gestur cubit untuk memperbesar detail tertentu.
7. Tekan tombol "Simpan ke Galeri" untuk menyimpan berkas resolusi tinggi ke album perangkat. Gunakan tautan "Buka" pada notifikasi bawah untuk langsung meninjau hasil di aplikasi galeri bawaan.

---

## Catatan Kinerja

Waktu pemrosesan bergantung pada resolusi gambar input, pilihan faktor perbesaran, serta kemampuan komputasi GPU perangkat. Sebagai gambaran kinerja pada pengujian perangkat kelas menengah (chipset MediaTek Helio G85 / GPU Mali-G52 MC2):
- Gambar input 300 x 400 piksel dengan faktor perbesaran 2x (hasil 600 x 800 piksel) diselesaikan dalam kurun waktu sekitar 20-30 detik.
- Gambar input 300 x 400 piksel dengan faktor perbesaran 4x (hasil 1200 x 1600 piksel, 12 ubin komputasi) diselesaikan dalam kurun waktu sekitar 90-95 detik.

Penggunaan arsitektur ubin (tiling) memastikan aplikasi tidak mengalami kendala kehabisan memori (Out-of-Memory) bahkan saat memproses gambar beresolusi tinggi.

