package com.upscaleyuk.app

import android.content.res.AssetManager
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * UpscaleEngine: Kotlin singleton wrapper untuk operasi native NCNN & Real-ESRGAN.
 *
 * Menerapkan arsitektur Ponytail: 1 model AI (realesrgan-x4plus) untuk 2x, 4x, dan 8x,
 * memori safety guard (<= 8192 px), dan pembersihan bitmap perantara (.recycle()).
 */
object UpscaleEngine {
    init {
        System.loadLibrary("upscaleyuk")
    }

    private external fun nativeInit(assetManager: AssetManager): Boolean
    private external fun nativeTestTile(): String
    private external fun nativeUpscale4x(
        input: Bitmap,
        output: Bitmap,
        onProgress: (Int) -> Unit
    ): Boolean

    var isInitialized: Boolean = false
        private set

    fun init(assetManager: AssetManager): Boolean {
        if (isInitialized) return true
        val success = nativeInit(assetManager)
        isInitialized = success
        return success
    }

    fun testTile(): String {
        if (!isInitialized) return "FAIL: Not initialized"
        return nativeTestTile()
    }

    /**
     * Memproses perbesaran gambar dengan pilihan skala 2, 4, atau 8.
     * Berjalan sepenuhnya di Dispatchers.Default (background thread).
     */
    suspend fun upscale(
        source: Bitmap,
        scale: Int, // 2, 4, atau 8
        onProgress: (Int) -> Unit = {}
    ): Bitmap = withContext(Dispatchers.Default) {
        require(scale in listOf(2, 4, 8)) { "Skala tidak valid: $scale (hanya 2, 4, atau 8)" }
        if (!isInitialized) {
            throw IllegalStateException("UpscaleEngine belum diinisialisasi")
        }

        // 1. Memory Safety Guard: Batasi output maksimum 8192 px
        val maxOutputDim = 8192
        val targetW = source.width * scale
        val targetH = source.height * scale

        val safeSource = if (targetW > maxOutputDim || targetH > maxOutputDim) {
            val factor = maxOutputDim.toFloat() / maxOf(targetW, targetH).toFloat()
            val safeW = (source.width * factor).toInt().coerceAtLeast(1)
            val safeH = (source.height * factor).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(source, safeW, safeH, true)
        } else {
            source
        }

        // 2. Alokasi buffer AI 4x
        val aiW = safeSource.width * 4
        val aiH = safeSource.height * 4
        val bitmap4x = Bitmap.createBitmap(aiW, aiH, Bitmap.Config.ARGB_8888)

        // 3. Jalankan inferensi native AI 4x
        val success = nativeUpscale4x(safeSource, bitmap4x, onProgress)
        if (safeSource != source) {
            safeSource.recycle()
        }
        if (!success) {
            bitmap4x.recycle()
            throw RuntimeException("nativeUpscale4x gagal diproses oleh C++")
        }

        // 4. Transformasi skala akhir & pembersihan memori sementara
        val finalW = source.width * scale
        val finalH = source.height * scale
        val finalSafeW = if (finalW > maxOutputDim || finalH > maxOutputDim) {
            val factor = maxOutputDim.toFloat() / maxOf(finalW, finalH).toFloat()
            (finalW * factor).toInt()
        } else finalW
        val finalSafeH = if (finalW > maxOutputDim || finalH > maxOutputDim) {
            val factor = maxOutputDim.toFloat() / maxOf(finalW, finalH).toFloat()
            (finalH * factor).toInt()
        } else finalH

        when (scale) {
            4 -> bitmap4x
            2 -> {
                // Mode 2x: Supersampling downscale dari 4x
                val bmp2x = Bitmap.createScaledBitmap(bitmap4x, finalSafeW, finalSafeH, true)
                bitmap4x.recycle()
                bmp2x
            }
            8 -> {
                // Mode 8x: Filtered scaling dari 4x
                val bmp8x = Bitmap.createScaledBitmap(bitmap4x, finalSafeW, finalSafeH, true)
                bitmap4x.recycle()
                bmp8x
            }
            else -> bitmap4x
        }
    }
}
