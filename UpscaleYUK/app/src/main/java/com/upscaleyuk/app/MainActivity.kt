package com.upscaleyuk.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

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
private val ColorTertiary = Color(0xFFF2C879) // Khusus Handle & Knob slider Before/After

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fase 10: Edge-to-Edge Dark Modern System Bars (Status & Navigation bar menyatu mulus)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false

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
        var upscaledScale by remember { mutableIntStateOf(4) }
        var isProcessing by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var progress by remember { mutableIntStateOf(0) }
        var statusMessage by remember { mutableStateOf("") }

        // Fase 10: Proteksi Layar selama Inferensi AI (FLAG_KEEP_SCREEN_ON dinamis)
        DisposableEffect(isProcessing) {
            if (isProcessing) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

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

        // Dukungan intent.data untuk integrasi eksternal & automated testing
        androidx.compose.runtime.LaunchedEffect(intent?.data) {
            Log.d("UpscaleYUK", "LaunchedEffect received intent.data: ${intent?.data}")
            intent?.data?.let { uri ->
                val loaded = loadBitmapWithExif(this@MainActivity, uri)
                Log.d("UpscaleYUK", "Loaded bitmap from intent: $loaded")
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
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
                        val currentSource = selectedBitmap
                        if (currentSource != null) {
                            BeforeAfterPreview(
                                originalBitmap = currentSource,
                                upscaledBitmap = upscaledBitmap,
                                selectedScale = if (upscaledBitmap != null) upscaledScale else selectedScale,
                                modifier = Modifier.fillMaxSize()
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
                        upscaledScale = upscaledScale,
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
                                    val startTime = System.currentTimeMillis()
                                    try {
                                        val result = UpscaleEngine.upscale(source, selectedScale) { pct ->
                                            progress = pct
                                        }
                                        val durationSec = (System.currentTimeMillis() - startTime) / 1000f
                                        upscaledBitmap?.recycle()
                                        upscaledBitmap = result
                                        upscaledScale = selectedScale
                                        statusMessage = String.format("Selesai dalam %.1f detik • %d × %d px", durationSec, result.width, result.height)
                                    } catch (e: Exception) {
                                        Log.e("UpscaleYUK", "Upscale gagal", e)
                                        statusMessage = "Gagal: ${e.message}"
                                    } finally {
                                        isProcessing = false
                                    }
                                }
                            }
                        },
                        onSaveImage = {
                            val targetBitmap = upscaledBitmap
                            if (targetBitmap != null && !isSaving && !isProcessing) {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val savedUri = saveBitmapToGallery(
                                            context = this@MainActivity,
                                            bitmap = targetBitmap,
                                            scale = upscaledScale
                                        )
                                        if (savedUri != null) {
                                            statusMessage = "Tersimpan di Pictures/UpscaleYUK"
                                            val snackbarResult = snackbarHostState.showSnackbar(
                                                message = "✓ Tersimpan di Pictures/UpscaleYUK",
                                                actionLabel = "Buka",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (snackbarResult == SnackbarResult.ActionPerformed) {
                                                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(savedUri, "image/png")
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                try {
                                                    startActivity(viewIntent)
                                                } catch (e: Exception) {
                                                    Log.e("UpscaleYUK", "Tidak dapat membuka galeri untuk URI: $savedUri", e)
                                                }
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                message = "Gagal menyimpan foto. Silakan coba lagi.",
                                                duration = SnackbarDuration.Short
                                            )
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UpscaleYUK", "Error saat menyimpan ke galeri", e)
                                        snackbarHostState.showSnackbar(
                                            message = "Gagal: ${e.message}",
                                            duration = SnackbarDuration.Short
                                        )
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            }
                        },
                        hasImage = selectedBitmap != null,
                        hasUpscaledImage = upscaledBitmap != null,
                        isProcessing = isProcessing,
                        isSaving = isSaving,
                        progress = progress,
                        statusMessage = statusMessage
                    )
                }

                // SnackbarHost Material 3 melayang di atas kontrol bawah (F9/Snackbar/Saved/dark)
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = ColorSurfaceVariant,
                        contentColor = ColorOnSurface,
                        actionColor = ColorPrimary,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Komponen BeforeAfterPreview (Fase 8):
 * - Sebelum di-upscale: Merender foto asli secara penuh.
 * - Setelah di-upscale: Merender komparasi Before/After terbelah dengan garis pembatas slider.
 * - Slider Handle: Garis 2 dp ColorTertiary + Knob 44 dp (F8/BeforeAfter/Done-50/dark).
 * - Synchronized Zoom & Pan: Pinch-to-zoom (1x..5x) dan pan sinkron untuk kedua lapisan.
 * - Signature Reveal: Memicu animasi sapuan 100% -> 50% saat upscaledBitmap siap.
 */
@Composable
fun BeforeAfterPreview(
    originalBitmap: Bitmap,
    upscaledBitmap: Bitmap?,
    selectedScale: Int,
    modifier: Modifier = Modifier
) {
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    val sliderAnim = remember { Animatable(1f) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Memicu signature reveal animation saat hasil upscale siap (1.0f -> 0.5f)
    LaunchedEffect(upscaledBitmap) {
        if (upscaledBitmap != null) {
            sliderAnim.snapTo(1f)
            sliderAnim.animateTo(
                targetValue = 0.5f,
                animationSpec = tween(
                    durationMillis = 700,
                    easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
                )
            )
        } else {
            sliderAnim.snapTo(1f)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                // Double tap untuk toggle zoom (1x <-> 2.5x)
                detectTapGestures(
                    onDoubleTap = {
                        zoomScale = if (zoomScale > 1f) 1f else 2.5f
                        panOffset = Offset.Zero
                    }
                )
            }
            .pointerInput(Unit) {
                // Synchronized Pinch-to-Zoom dan Pan
                detectTransformGestures { _, pan, zoom, _ ->
                    zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                    if (zoomScale > 1f) {
                        panOffset += pan
                    } else {
                        panOffset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight
        val density = LocalDensity.current
        val widthPx = with(density) { containerWidth.toPx() }

        val sliderPos = sliderAnim.value.coerceIn(0f, 1f)

        // Transformasi zoom & pan yang SAMA untuk kedua lapisan (ukuran & posisi gambar identik 1:1)
        val zoomPanLayer: Modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = zoomScale
                scaleY = zoomScale
                translationX = panOffset.x
                translationY = panOffset.y
            }

        if (upscaledBitmap == null) {
            // State Foto Asli Penuh (Sebelum AI Upscale)
            Image(
                bitmap = originalBitmap.asImageBitmap(),
                contentDescription = "Foto Asli",
                modifier = zoomPanLayer.padding(12.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            // 1. Lapisan Bawah: Foto Hasil Upscale, ukuran penuh
            Image(
                bitmap = upscaledBitmap.asImageBitmap(),
                contentDescription = "Hasil Upscale",
                modifier = zoomPanLayer.padding(12.dp),
                contentScale = ContentScale.Fit
            )

            // 2. Lapisan Atas: Foto Asli, ukuran penuh yang SAMA, hanya DIPOTONG (clipRect)
            //    di sisi kiri garis slider (koordinat layar). Gambar tidak mengecil/menyempit.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * sliderPos) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                Image(
                    bitmap = originalBitmap.asImageBitmap(),
                    contentDescription = "Foto Asli (Before)",
                    modifier = zoomPanLayer.padding(12.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // Kontrol Slider & Badge Overlay (Hanya muncul saat hasil AI tersedia)
        if (upscaledBitmap != null) {
            // Garis Pembatas Vertikal (2 dp) + Touch Handle Knob (44 dp)
            val lineOffsetDp = containerWidth * sliderPos

            // Garis Vertikal Slider (Tertiary #F2C879)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxHeight()
                    .width(2.dp)
                    .offset { IntOffset((lineOffsetDp.toPx()).roundToInt() - with(density) { 1.dp.toPx().roundToInt() }, 0) }
                    .background(ColorTertiary)
            )

            // Knob Sentuh Slider (44 dp, touch target empuk di tengah vertikal)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .offset {
                        IntOffset(
                            (lineOffsetDp.toPx() - with(density) { 22.dp.toPx() }).roundToInt(),
                            (with(density) { (containerHeight / 2).toPx() - 22.dp.toPx() }).roundToInt()
                        )
                    }
                    .clip(CircleShape)
                    .background(ColorTertiary)
                    .border(2.dp, ColorSurface, CircleShape)
                    .pointerInput(widthPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDraggingSlider = true },
                            onDragEnd = { isDraggingSlider = false },
                            onDragCancel = { isDraggingSlider = false },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val newPos = (sliderAnim.value + (dragAmount / widthPx)).coerceIn(0f, 1f)
                                coroutineScope.launch {
                                    sliderAnim.snapTo(newPos)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Indikator panah kiri/kanan di dalam knob
                Text(
                    text = "◂ ▸",
                    color = ColorSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Badge Label "Asli" (Pojok Kiri Bawah)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorSurfaceContainer.copy(alpha = 0.85f))
                    .border(1.dp, ColorOutline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Asli",
                    color = ColorOnSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Badge Label "Hasil [scale]x" (Pojok Kanan Bawah, tap untuk toggle zoom 1x <-> 2.5x)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ColorPrimary)
                    .clickable {
                        zoomScale = if (zoomScale > 1f) 1f else 2.5f
                        panOffset = Offset.Zero
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Hasil ${selectedScale}x",
                    color = ColorOnPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Komponen ControlPanel (Fase 7, 8, & 9):
 * Menggabungkan Selector Skala, Estimasi Resolusi, Tombol Pilih/Ganti Foto,
 * Tombol Mulai Upscale / Simpan ke Galeri, dan Progress Bar real-time.
 */
@Composable
fun ControlPanel(
    originalWidth: Int,
    originalHeight: Int,
    selectedScale: Int,
    upscaledScale: Int,
    onScaleSelect: (Int) -> Unit,
    onPickImage: () -> Unit,
    onStartUpscale: () -> Unit,
    onSaveImage: () -> Unit,
    hasImage: Boolean,
    hasUpscaledImage: Boolean,
    isProcessing: Boolean,
    isSaving: Boolean,
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
                            .clickable(enabled = !isProcessing && !isSaving) { onScaleSelect(scale) },
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

            // Info Estimasi Resolusi / Durasi Proses
            val estText = if (statusMessage.startsWith("Selesai") || statusMessage.startsWith("Tersimpan")) {
                statusMessage
            } else if (hasImage) {
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
                textAlign = TextAlign.Center,
                style = TextStyle(fontFeatureSettings = "tnum")
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
                        fontSize = 12.sp,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                    Text(
                        text = "$progress%",
                        color = ColorPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontFeatureSettings = "tnum")
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
                    enabled = !isProcessing && !isSaving,
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

                // Tombol Mulai Upscale / Simpan ke Galeri
                if (hasImage) {
                    val isSaveAction = hasUpscaledImage && (selectedScale == upscaledScale) && !isProcessing
                    Button(
                        onClick = {
                            if (isSaveAction) {
                                onSaveImage()
                            } else {
                                onStartUpscale()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        enabled = !isProcessing && !isSaving,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorPrimary,
                            contentColor = ColorOnPrimary,
                            disabledContainerColor = ColorPrimary.copy(alpha = 0.3f),
                            disabledContentColor = ColorOnPrimary.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = when {
                                isProcessing -> "Memproses..."
                                isSaving -> "Menyimpan..."
                                isSaveAction -> "Simpan ke Galeri"
                                else -> "Mulai Upscale"
                            },
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
        Log.d("UpscaleYUK", "loadBitmapWithExif URI: $uri, scheme: ${uri.scheme}, path: ${uri.path}")
        val openStream = {
            if (uri.scheme == "file" || uri.scheme == null) {
                java.io.FileInputStream(java.io.File(uri.path ?: uri.toString()))
            } else {
                context.contentResolver.openInputStream(uri)
            }
        }
        val inputStream = openStream() ?: run {
            Log.e("UpscaleYUK", "openStream returned null for $uri")
            return null
        }
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        val exifStream = openStream() ?: return originalBitmap
        val exif = ExifInterface(exifStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        exifStream.close()

        val matrix = Matrix()
        var needsRotation = false
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> { matrix.postRotate(90f); needsRotation = true }
            ExifInterface.ORIENTATION_ROTATE_180 -> { matrix.postRotate(180f); needsRotation = true }
            ExifInterface.ORIENTATION_ROTATE_270 -> { matrix.postRotate(270f); needsRotation = true }
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { matrix.postScale(-1f, 1f); needsRotation = true }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.postScale(1f, -1f); needsRotation = true }
        }

        val rotatedBitmap = if (needsRotation) {
            val rotated = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, true
            )
            if (rotated != originalBitmap) originalBitmap.recycle()
            rotated
        } else {
            originalBitmap
        }

        // Batasi resolusi input preview maksimal 400 px agar proses inferensi di HP cepat dan responsif (<= 12 tiles)
        val maxInputDim = 400
        val finalBitmap = if (rotatedBitmap.width > maxInputDim || rotatedBitmap.height > maxInputDim) {
            val factor = maxInputDim.toFloat() / maxOf(rotatedBitmap.width, rotatedBitmap.height).toFloat()
            val targetW = (rotatedBitmap.width * factor).toInt().coerceAtLeast(1)
            val targetH = (rotatedBitmap.height * factor).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rotatedBitmap, targetW, targetH, true)
            rotatedBitmap.recycle()
            scaled
        } else {
            rotatedBitmap
        }
        finalBitmap
    } catch (e: Exception) {
        Log.e("UpscaleYUK", "Gagal memuat bitmap dengan EXIF", e)
        null
    }
}

/**
 * Menyimpan Bitmap ke Galeri Foto menggunakan Scoped Storage (MediaStore) Android modern.
 * Berjalan di Dispatchers.IO dengan flag IS_PENDING untuk mencegah korupsi file saat penulisan.
 * Sesuai spesifikasi Fase 9 (UpscaleYuk_Blueprints.md).
 */
suspend fun saveBitmapToGallery(context: Context, bitmap: Bitmap, scale: Int): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val filename = "UpscaleYUK_${scale}x_${System.currentTimeMillis()}.png"

    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/UpscaleYUK")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }

    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    var itemUri: Uri? = null
    try {
        itemUri = resolver.insert(collection, contentValues) ?: return@withContext null
        resolver.openOutputStream(itemUri)?.use { outputStream ->
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            if (!compressed) throw IOException("Gagal mengompresi bitmap ke stream output.")
            outputStream.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }
        Log.d("UpscaleYUK", "saveBitmapToGallery sukses: $itemUri ($filename)")
        itemUri
    } catch (e: Exception) {
        Log.e("UpscaleYUK", "Gagal menyimpan bitmap ke galeri", e)
        itemUri?.let { uri ->
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
        }
        null
    }
}
