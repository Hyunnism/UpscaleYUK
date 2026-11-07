#include <jni.h>
#include <string>
#include <chrono>
#include <algorithm>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include "net.h"
#include "gpu.h"
#include "cpu.h"

#define TAG "UpscaleYUK_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Compatibility stub for NDK r27+ linker with older prebuilt glslang
namespace std {
inline namespace __ndk1 {
    size_t __hash_memory(const void* ptr, size_t n) noexcept {
        const auto* data = static_cast<const unsigned char*>(ptr);
        size_t hash = 14695981039346656037ULL;
        for (size_t i = 0; i < n; ++i) {
            hash ^= data[i];
            hash *= 1099511628211ULL;
        }
        return hash;
    }
}
}

static ncnn::Net* g_net = nullptr;
static ncnn::VulkanDevice* g_vkdev = nullptr;

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnLoad called - initializing NCNN GPU instance");
    ncnn::create_gpu_instance();
    return JNI_VERSION_1_6;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    LOGI("JNI_OnUnload called - cleaning up NCNN GPU instance");
    if (g_net) {
        delete g_net;
        g_net = nullptr;
    }
    ncnn::destroy_gpu_instance();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_upscaleyuk_app_UpscaleEngine_nativeInit(
    JNIEnv* env,
    jobject /* thiz */,
    jobject assetManager) {

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);
    if (!mgr) {
        LOGE("AssetManager is null!");
        return JNI_FALSE;
    }

    if (!g_net) {
        g_net = new ncnn::Net();
    } else {
        g_net->clear();
    }

    // 1. Cek ketersediaan Vulkan GPU
    int gpu_count = ncnn::get_gpu_count();
    bool use_gpu = (gpu_count > 0);

    g_net->opt = ncnn::Option();
    if (use_gpu) {
        int gpu_device = 0;
        g_vkdev = ncnn::get_gpu_device(gpu_device);
        g_net->opt.use_vulkan_compute = true;
        g_net->opt.use_fp16_packed = true;
        g_net->opt.use_fp16_storage = true;
        g_net->opt.use_fp16_arithmetic = true;
        g_net->set_vulkan_device(g_vkdev);
        LOGI("Configured NCNN for Vulkan GPU device 0 (%s)", g_vkdev->info.device_name());
    } else {
        g_net->opt.use_vulkan_compute = false;
        g_net->opt.num_threads = ncnn::get_big_cpu_count();
        LOGI("Configured NCNN with CPU fallback (%d threads)", g_net->opt.num_threads);
    }

    // 2. Load model params (.param) langsung dari assets
    int ret_param = g_net->load_param(mgr, "realesrgan-x4plus.param");
    if (ret_param != 0) {
        LOGE("Failed to load realesrgan-x4plus.param from assets (error code: %d)", ret_param);
        return JNI_FALSE;
    }

    // 3. Load model weights (.bin) langsung dari assets
    int ret_model = g_net->load_model(mgr, "realesrgan-x4plus.bin");
    if (ret_model != 0) {
        LOGE("Failed to load realesrgan-x4plus.bin from assets (error code: %d)", ret_model);
        return JNI_FALSE;
    }

    LOGI("NCNN Init Success | realesrgan-x4plus loaded | Vulkan GPU: %s",
         use_gpu ? "Active" : "CPU Fallback");

    return JNI_TRUE;
}

static bool processTile(const ncnn::Mat& inTile, ncnn::Mat& outTile) {
    if (!g_net) {
        LOGE("processTile error: g_net is null!");
        return false;
    }

    // 1. Normalisasi: [0..255] -> [0.0..1.0]
    ncnn::Mat in = inTile.clone();
    const float norm_vals[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    in.substract_mean_normalize(0, norm_vals);

    // 2. Extractor pada GPU Vulkan pipeline
    ncnn::Extractor ex = g_net->create_extractor();
    ex.input("data", in);

    // 3. Ekstraksi blob output 4x
    ncnn::Mat out;
    int ret = ex.extract("output", out);
    if (ret != 0) {
        LOGE("processTile failed to extract blob 'output' (error code: %d)", ret);
        return false;
    }

    // 4. Denormalisasi: [0.0..1.0] -> [0..255]
    const float denorm_vals[3] = {255.f, 255.f, 255.f};
    out.substract_mean_normalize(0, denorm_vals);

    // 5. Clamping aman pada rentang RGB [0..255]
    for (int q = 0; q < out.c; q++) {
        float* ptr = out.channel(q);
        int size = out.w * out.h;
        for (int i = 0; i < size; i++) {
            if (ptr[i] < 0.f) ptr[i] = 0.f;
            else if (ptr[i] > 255.f) ptr[i] = 255.f;
        }
    }

    outTile = out;
    return true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_upscaleyuk_app_UpscaleEngine_nativeTestTile(
    JNIEnv* env,
    jobject /* thiz */) {

    if (!g_net) {
        return env->NewStringUTF("FAIL: Engine not initialized");
    }

    // Matriks sintetis 64x64x3 dengan nilai konstan 128.f
    ncnn::Mat inTile(64, 64, 3);
    inTile.fill(128.f);

    auto start = std::chrono::high_resolution_clock::now();
    ncnn::Mat outTile;
    bool success = processTile(inTile, outTile);
    auto end = std::chrono::high_resolution_clock::now();

    double elapsed_ms = std::chrono::duration<double, std::milli>(end - start).count();

    if (!success) {
        return env->NewStringUTF("FAIL: processTile returned false");
    }

    // Ambil sampel nilai piksel di koordinat tengah (128, 128)
    float sample_val = outTile.channel(0).row(128)[128];

    char report[256];
    snprintf(report, sizeof(report),
        "PASS | in: %dx%dx%d -> out: %dx%dx%d | sample: %.1f | %.1f ms",
        inTile.w, inTile.h, inTile.c,
        outTile.w, outTile.h, outTile.c,
        sample_val, elapsed_ms
    );
    LOGI("Test Single Tile: %s", report);

    return env->NewStringUTF(report);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_upscaleyuk_app_UpscaleEngine_nativeUpscale4x(
    JNIEnv* env,
    jobject /* thiz */,
    jobject inputBitmap,
    jobject outputBitmap,
    jobject progressCallback) {

    if (!g_net) {
        LOGE("nativeUpscale4x error: g_net is null!");
        return JNI_FALSE;
    }

    AndroidBitmapInfo inInfo;
    if (AndroidBitmap_getInfo(env, inputBitmap, &inInfo) < 0) {
        LOGE("Failed to get input bitmap info");
        return JNI_FALSE;
    }

    AndroidBitmapInfo outInfo;
    if (AndroidBitmap_getInfo(env, outputBitmap, &outInfo) < 0) {
        LOGE("Failed to get output bitmap info");
        return JNI_FALSE;
    }

    if (inInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Bitmaps must be RGBA_8888 format");
        return JNI_FALSE;
    }

    int width = inInfo.width;
    int height = inInfo.height;
    int out_width = outInfo.width;
    int out_height = outInfo.height;

    if (out_width != width * 4 || out_height != height * 4) {
        LOGE("Output bitmap must be exactly 4x input size (%dx%d vs %dx%d)",
             out_width, out_height, width * 4, height * 4);
        return JNI_FALSE;
    }

    void* src_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, inputBitmap, &src_pixels) < 0 || !src_pixels) {
        LOGE("Failed to lock input bitmap pixels");
        return JNI_FALSE;
    }

    void* dst_pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, outputBitmap, &dst_pixels) < 0 || !dst_pixels) {
        LOGE("Failed to lock output bitmap pixels");
        AndroidBitmap_unlockPixels(env, inputBitmap);
        return JNI_FALSE;
    }

    int src_stride = inInfo.stride;
    int dst_stride = outInfo.stride;

    // Persiapan callback progress ke Kotlin
    jclass funcClass = progressCallback ? env->GetObjectClass(progressCallback) : nullptr;
    jmethodID invokeMid = funcClass ? env->GetMethodID(funcClass, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;") : nullptr;
    jclass intClass = env->FindClass("java/lang/Integer");
    jmethodID valueOfMid = env->GetStaticMethodID(intClass, "valueOf", "(I)Ljava/lang/Integer;");

    const int tile_size = 128;
    const int prepadding = 10;
    const int scale = 4;

    int tiles_x = (width + tile_size - 1) / tile_size;
    int tiles_y = (height + tile_size - 1) / tile_size;
    int total_tiles = tiles_x * tiles_y;

    LOGI("nativeUpscale4x start: %dx%d -> %dx%d | Grid: %dx%d (%d tiles total)",
         width, height, out_width, out_height, tiles_x, tiles_y, total_tiles);

    int processed_tiles = 0;
    bool success = true;

    for (int ty = 0; ty < tiles_y; ty++) {
        for (int tx = 0; tx < tiles_x; tx++) {
            int x = tx * tile_size;
            int y = ty * tile_size;
            int tile_w = std::min(tile_size, width - x);
            int tile_h = std::min(tile_size, height - y);

            // Tentukan area baca dari input beserta padding yang tersedia
            int in_x = std::max(0, x - prepadding);
            int in_y = std::max(0, y - prepadding);
            int in_x_end = std::min(width, x + tile_w + prepadding);
            int in_y_end = std::min(height, y + tile_h + prepadding);

            int in_w = in_x_end - in_x;
            int in_h = in_y_end - in_y;

            // Hitung padding yang perlu direplikasi jika menyentuh tepi kanvas gambar
            int pad_left = (x - prepadding < 0) ? (prepadding - x) : 0;
            int pad_top = (y - prepadding < 0) ? (prepadding - y) : 0;
            int pad_right = (x + tile_w + prepadding > width) ? (x + tile_w + prepadding - width) : 0;
            int pad_bottom = (y + tile_h + prepadding > height) ? (y + tile_h + prepadding - height) : 0;

            // Baca ROI piksel dari inputBitmap (RGBA -> RGB)
            ncnn::Mat tile = ncnn::Mat::from_pixels_roi(
                (const unsigned char*)src_pixels,
                ncnn::Mat::PIXEL_RGBA2RGB,
                width, height, src_stride,
                in_x, in_y, in_w, in_h
            );

            // Terapkan border replication jika ada padding tepi
            ncnn::Mat tile_padded;
            if (pad_left > 0 || pad_top > 0 || pad_right > 0 || pad_bottom > 0) {
                ncnn::copy_make_border(tile, tile_padded, pad_top, pad_bottom, pad_left, pad_right, ncnn::BORDER_REPLICATE, 0.f);
            } else {
                tile_padded = tile;
            }

            // Eksekusi inferensi AI di GPU
            ncnn::Mat out_padded;
            if (!processTile(tile_padded, out_padded)) {
                LOGE("Failed to process tile (%d, %d)", tx, ty);
                success = false;
                break;
            }

            // Pangkas border overlap ekstra (10 px x 4 = 40 px di setiap sisi)
            ncnn::Mat out_tile;
            ncnn::copy_cut_border(
                out_padded,
                out_tile,
                prepadding * scale,
                prepadding * scale,
                prepadding * scale,
                prepadding * scale
            );

            // Tulis piksel hasil langsung ke outputBitmap (RGB -> RGBA)
            int dst_x = x * scale;
            int dst_y = y * scale;
            unsigned char* dst_ptr = ((unsigned char*)dst_pixels) + (dst_y * dst_stride) + (dst_x * 4);
            out_tile.to_pixels(dst_ptr, ncnn::Mat::PIXEL_RGB2RGBA, dst_stride);

            processed_tiles++;
            int percent = (processed_tiles * 100) / total_tiles;

            LOGI("Progress tile %d/%d (%d%%) [x=%d, y=%d, size=%dx%d]",
                 processed_tiles, total_tiles, percent, x, y, tile_w, tile_h);

            // Callback progress ke Kotlin
            if (progressCallback && invokeMid && valueOfMid) {
                jobject intObj = env->CallStaticObjectMethod(intClass, valueOfMid, percent);
                env->CallObjectMethod(progressCallback, invokeMid, intObj);
                env->DeleteLocalRef(intObj);
            }
        }
        if (!success) break;
    }

    AndroidBitmap_unlockPixels(env, inputBitmap);
    AndroidBitmap_unlockPixels(env, outputBitmap);

    LOGI("nativeUpscale4x completed: %s", success ? "SUCCESS" : "FAILED");
    return success ? JNI_TRUE : JNI_FALSE;
}

