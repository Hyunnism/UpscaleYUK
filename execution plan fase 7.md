# Execution Plan — Fase 7: Fungsi UI #1 — Pemilih Gambar & Skala (`ControlPanel`)

> **Status:** **Selesai / PASS** ✅  
> **Turunan Dokumen:** [UpscaleYuk_Blueprints.md](./UpscaleYuk_Blueprints.md) (Fase 7), [docs/DESIGN.md](./docs/DESIGN.md), & [docs/WORKFLOW.md](./docs/WORKFLOW.md)  
> **Prinsip Arsitektur:** **Ponytail Mode: Full** (YAGNI, minimalis, efisiensi memori, tanpa dependensi pihak ketiga, 4 file kode utama, konsistensi token Material 3 dark-first).

---

## 1. Tujuan Tunggal Fase 7

Mengimplementasikan **1 komponen antarmuka kontrol utama (`ControlPanel`)** dan mengintegrasikan integrasi pemilih foto sistem Android (`PickVisualMedia`) di `MainActivity.kt` yang bertugas:
1. Membuka Android Photo Picker resmi (`ActivityResultContracts.PickVisualMedia`) untuk memilih foto dari galeri tanpa memerlukan izin penyimpanan berbahaya (`READ_EXTERNAL_STORAGE`).
2. Membaca `Bitmap` dari URI galeri dan mengoreksi orientasi EXIF kamera secara otomatis menggunakan `androidx.exifinterface.media.ExifInterface` (agar foto tidak terbalik/miring).
3. Menampilkan pemilih skala segmentasi `2x`, `4x`, dan `8x` (`SingleChoiceSegmentedButtonRow` / Styled Segmented Buttons) yang memperbarui state aktif dan mengkalkulasi estimasi resolusi akhir secara langsung (`W×H px → TargetW×TargetH px`).
4. Mengontrol interaksi tombol dan progress bar:
   - Tombol **"Pilih Foto"** (`FilledTonalButton` saat kosong -> `OutlinedButton` setelah foto terisi).
   - Tombol **"Mulai Upscale"** (`Button` primary Material 3) — **satu-satunya aksi primary** di layar.
   - Mengunci seluruh tombol (`enabled = false`) saat pemrosesan berlangsung (`isProcessing == true`).
   - Menampilkan `LinearProgressIndicator` dan label persentase `0% - 100%` secara *real-time*.

> [!IMPORTANT]
> **Batasan Ketat Fase 7 (Anti Over-Engineering):**
> * **TIDAK BOLEH** membuat *Before/After Split Slider* atau kanvas pemotong interaktif di fase ini (itu adalah fokus eksklusif Fase 8).
> * **TIDAK BOLEH** membuat fungsi penyimpanan `MediaStore` atau tombol Simpan ke Galeri (itu adalah fokus Fase 9).
> * **TIDAK BOLEH** menambah file Composable baru atau modul terpisah (semua Composable tetap terorganisir rapi di dalam `MainActivity.kt` sesuai prinsip 4 file kode utama).

---

## 2. Struktur File & Modifikasi Kode

```text
UpscaleYUK/
└── app/
    ├── build.gradle.kts          # Tambah: androidx.exifinterface:exifinterface:1.3.7 (untuk koreksi rotasi foto galeri)
    └── src/main/java/com/upscaleyuk/app/
        └── MainActivity.kt       # Implementasi ControlPanel + State Management + Photo Picker Contract
```

---

## 3. Rincian Langkah Eksekusi (Action Items)

### Langkah 1: Tambah Dependensi Resmi `exifinterface` di `build.gradle.kts`
Untuk membaca tag rotasi EXIF dari gambar galeri tanpa menulis parser EXIF manual:
```kotlin
implementation("androidx.exifinterface:exifinterface:1.3.7")
```

### Langkah 2: Implementasi Fungsi Helper EXIF & Bitmap Loader di `MainActivity.kt`
- Fungsi `loadBitmapWithExif(context: Context, uri: Uri): Bitmap?`
- Membaca stream URI, mendeteksi `TAG_ORIENTATION` (90°, 180°, 270°), dan memutar `Bitmap` via `android.graphics.Matrix` jika diperlukan.

### Langkah 3: Implementasi Komponen Composable `ControlPanel`
Sesuai spek token di [docs/DESIGN.md](./docs/DESIGN.md):
- **Warna & Permukaan:** `surfaceContainer` (`#181C1B`), `surfaceVariant` (`#232827`), `primary` (`#7BD8B8`), `onPrimary` (`#00382A`), `onSurface` (`#E1E4E2`), `onSurfaceVariant` (`#A9B0AD`).
- **Dimensi & Spacing:** Grid 8 dp, sudut atas 28 dp, touch target minimal 48×48 dp.
- **Teks Resolusi:** `bodyMedium` dengan angka tabular (`fontFeatureSettings = "tnum"`) format: `1080×1080 px → 4320×4320 px`.
- **Indikator Progres:** `LinearProgressIndicator` + `labelMedium` persentase progress saat `isProcessing == true`.

Spesifikasi fungsi:
```kotlin
@Composable
fun ControlPanel(
    originalWidth: Int,
    originalHeight: Int,
    selectedScale: Int,
    onScaleSelect: (Int) -> Unit,
    onPickImage: () -> Unit,
    onStartUpscale: () -> Unit,
    hasImage: Boolean,
    isProcessing: Boolean,
    progress: Int
)
```

### Langkah 4: Hubungkan ke `UpscaleEngine.upscale` & Lifecycle UI
- `rememberLauncherForActivityResult` dengan `PickVisualMedia()`.
- State reactive:
  - `selectedBitmap`: `Bitmap?` (foto asli)
  - `upscaledBitmap`: `Bitmap?` (hasil AI)
  - `selectedScale`: `Int` (default 4)
  - `isProcessing`: `Boolean`
  - `progress`: `Int`
- Menampilkan pratinjau gambar foto yang dimuat di area flex preview (tinggi >= 55% layar) dengan rasio aspek terjaga (`ContentScale.Fit`).

---

## 4. Prosedur Testing di Perangkat Fisik (Xiaomi Redmi Note 9)

1. **Picker & EXIF Test:**
   - Masukkan foto galeri ke perangkat via adb:
     ```bash
     adb -s 462a07fa0405 push test_photo.jpg /sdcard/Download/
     ```
   - Buka aplikasi, klik *"Pilih Foto"*, pilih gambar dari galeri.
   - Verifikasi foto tampil tegak (rotasi EXIF benar) dan info resolusi awal muncul akurat (misal `1920×1080 px`).
2. **State Lock & Kalkulasi Dimensi Test:**
   - Klik tombol skala `2x`, `4x`, dan `8x`.
   - Pastikan teks estimasi resolusi langsung berubah secara dinamis (`1920×1080 px → 3840×2160 px`, `7680×4320 px`, dll.).
   - Klik *"Mulai Upscale"*: pastikan seluruh tombol menjadi non-aktif (*disabled*), progress bar bergerak halus dari `0%` hingga `100%`, dan tombol tidak dapat diklik ganda.
3. **Screenshot Bukti:**
   - Tangkap screenshot layar saat foto terisi (`docs/design/evidence/fase7_loaded_screen.png`).
   - Tangkap screenshot saat progress sedang berjalan (`docs/design/evidence/fase7_processing_screen.png`).

---

## 5. Kriteria Kelulusan (Definition of Done — Fase 7)

Fase 7 dinyatakan **`PASS`** jika dan hanya jika:
- [ ] Photo Picker sistem Android (`PickVisualMedia`) terbuka saat tombol *"Pilih Foto"* ditekan dan berhasil memuat `Bitmap` dengan orientasi EXIF yang benar.
- [ ] Pilihan tombol skala `2x`, `4x`, dan `8x` mengubah state aktif dan langsung memperbarui teks estimasi resolusi akhir (`W×H px → TargetW×TargetH px`).
- [ ] Saat `isProcessing == true`, seluruh tombol dikunci (*disabled*) dan indikator progress bar + persentase (`0% - 100%`) tampil secara *real-time*.
- [ ] Tampilan mematuhi aturan desain [docs/DESIGN.md](./docs/DESIGN.md) (token warna Material 3 dark-first `#101312`, `#181C1B`, `#7BD8B8`, touch target >= 48 dp, font tabular `tnum`).
- [ ] Tangkapan layar bukti eksekusi tersimpan di `docs/design/evidence/fase7_running_screen.png`.
- [ ] Checklist dan log hasil testing di `UpscaleYuk_Blueprints.md` (Fase 7) diperbarui ke `[x] Selesai` | `✅ PASS`.

