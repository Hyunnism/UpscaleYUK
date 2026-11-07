package com.upscaleyuk.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Palet Warna Material 3 Dark-first (Sesuai DESIGN.md & UpscaleYUK.figcraft)
 */
private val ColorSurface = Color(0xFF101312)
private val ColorSurfaceContainer = Color(0xFF181C1B)
private val ColorSurfaceVariant = Color(0xFF232827)
private val ColorOutline = Color(0xFF3C4442)
private val ColorOnSurface = Color(0xFFE1E4E2)
private val ColorOnSurfaceVariant = Color(0xFFA9B0AD)
private val ColorPrimary = Color(0xFF7BD8B8)
private val ColorOnPrimary = Color(0xFF00382A)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inisialisasi NCNN Real-ESRGAN Engine di background
        lifecycleScope.launch(Dispatchers.Default) {
            UpscaleEngine.init(assets)
        }

        setContent {
            UpscaleYukApp()
        }
    }

    @Composable
    fun UpscaleYukApp() {
        var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var upscaledBitmap by remember { mutableStateOf<Bitmap?>(null) }
        var selectedScale by remember { mutableIntStateOf(4) }
        var isProcessing by remember { mutableStateOf(false) }
        var progress by remember { mutableIntStateOf(0) }
        var statusMessage by remember { mutableStateOf("") }

        // Android Photo Picker resmi (PickVisualMedia)
        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia()
        ) { uri: Uri? ->
            uri?.let {
                val loaded = loadBitmapWithExif(this@MainActivity, it)
                if (loaded != null) {
                    selectedBitmap?.recycle()
                    upscaledBitmap?.recycle()
                    selectedBitmap = loaded
                    upscaledBitmap = null
                    progress = 0
                    statusMessage = "Foto siap (${loaded.width} × ${loaded.height} px)"
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ColorSurface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 1. TopAppBar (Tinggi 64 dp, padding horizontal 20 dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(ColorSurface)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "UpscaleYUK",
                        color = ColorOnSurface,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 2. Area Preview (Flex >= 55% ruang vertikal)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(ColorSurfaceContainer)
                        .border(1.dp, ColorOutline, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val currentImage = upscaledBitmap ?: selectedBitmap
                    if (currentImage != null) {
                        Image(
                            bitmap = currentImage.asImageBitmap(),
                            contentDescription = "Preview Gambar",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "Pilih foto dari galeri untuk memulai upscale AI",
                                color = ColorOnSurfaceVariant,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }

                // 3. ControlPanel (Permukaan Kontrol Bawah, sudut atas 28 dp)
                ControlPanel(
                    originalWidth = selectedBitmap?.width ?: 0,
                    originalHeight = selectedBitmap?.height ?: 0,
                    selectedScale = selectedScale,
                    onScaleSelect = { scale ->
                        if (!isProcessing) {
                            selectedScale = scale
                        }
                    },
                    onPickImage = {
                        if (!isProcessing) {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    },
                    onStartUpscale = {
                        val source = selectedBitmap
                        if (source != null && !isProcessing) {
                            isProcessing = true
                            progress = 0
                            statusMessage = "Memproses AI ${selectedScale}x..."

                            lifecycleScope.launch {
                                try {
                                    val result = UpscaleEngine.upscale(source, selectedScale) { pct ->
                                        progress = pct
                                    }
                                    upscaledBitmap?.recycle()
                                    upscaledBitmap = result
                                    statusMessage = "Selesai (${result.width} × ${result.height} px)"
                                } catch (e: Exception) {
                                    Log.e("UpscaleYUK", "Upscale gagal", e)
                                    statusMessage = "Gagal: ${e.message}"
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    hasImage = selectedBitmap != null,
                    isProcessing = isProcessing,
                    progress = progress,
                    statusMessage = statusMessage
                )
            }
        }
    }
}

/**
 * Komponen ControlPanel (Fase 7):
 * Menggabungkan Selector Skala, Estimasi Resolusi, Tombol Pilih/Ganti Foto,
 * Tombol Mulai Upscale, dan Progress Bar real-time.
 */
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
    progress: Int,
    statusMessage: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = ColorSurfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label Pilihan Skala
            Text(
                text = "Pilih Skala Resolusi",
                color = ColorOnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Selector Skala (2x | 4x | 8x)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(ColorSurfaceContainer)
                    .border(1.dp, ColorOutline, RoundedCornerShape(22.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(2, 4, 8).forEach { scale ->
                    val isSelected = selectedScale == scale
                    val bgColor = if (isSelected) ColorPrimary else ColorSurfaceVariant
                    val textColor = if (isSelected) ColorOnPrimary else ColorOnSurfaceVariant
                    val textWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(bgColor)
                            .clickable(enabled = !isProcessing) { onScaleSelect(scale) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${scale}x",
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = textWeight
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Info Estimasi Resolusi (Angka Tabular tnum agar stabil)
            val estText = if (hasImage) {
                val targetW = originalWidth * selectedScale
                val targetH = originalHeight * selectedScale
                "Estimasi Hasil: $targetW × $targetH px"
            } else {
                "Pilih foto untuk melihat estimasi resolusi"
            }

            Text(
                text = estText,
                color = if (hasImage) ColorPrimary else ColorOnSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Indikator Progress (determinate saat processing)
            if (isProcessing) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress / 100f,
                    animationSpec = tween(150),
                    label = "smoothProgress"
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = ColorPrimary,
                    trackColor = ColorSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = statusMessage,
                        color = ColorOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "$progress%",
                        color = ColorPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Baris Tombol Aksi (Tinggi minimal 48 dp, touch target nyaman)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tombol Pilih / Ganti Foto
                OutlinedButton(
                    onClick = onPickImage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    enabled = !isProcessing,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ColorSurfaceVariant,
                        contentColor = ColorOnSurface,
                        disabledContainerColor = ColorSurfaceVariant.copy(alpha = 0.5f),
                        disabledContentColor = ColorOnSurface.copy(alpha = 0.4f)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(ColorOutline)
                    )
                ) {
                    Text(
                        text = if (hasImage) "Ganti Foto" else "Pilih Foto",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Tombol Mulai Upscale (Hanya aktif jika sudah ada foto & tidak sedang processing)
                if (hasImage) {
                    Button(
                        onClick = onStartUpscale,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        enabled = !isProcessing,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = ColorOnPrimary,
                            disabledContainerColor = ColorPrimary.copy(alpha = 0.3f),
                            disabledContentColor = ColorOnPrimary.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = if (isProcessing) "Memproses..." else "Mulai Upscale",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Membaca Bitmap dari Uri dan mengoreksi orientasi EXIF kamera secara otomatis.
 */
private fun loadBitmapWithExif(context: Context, uri: Uri): Bitmap? {
    return try {
        // 1. Decode stream ke Bitmap
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        // 2. Baca orientasi EXIF
        val exifStream = context.contentResolver.openInputStream(uri) ?: return originalBitmap
        val exif = ExifInterface(exifStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        exifStream.close()

        // 3. Putar Bitmap jika perlu
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return originalBitmap
        }

        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0,
            originalBitmap.width, originalBitmap.height,
            matrix, true
        )
        if (rotatedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }
        rotatedBitmap
    } catch (e: Exception) {
        Log.e("UpscaleYUK", "Gagal memuat bitmap dengan EXIF", e)
        null
    }
}
