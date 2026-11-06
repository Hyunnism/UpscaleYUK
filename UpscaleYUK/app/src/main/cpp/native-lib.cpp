#include <jni.h>
#include <string>
#include <android/log.h>
#include "net.h"
#include "gpu.h"

#define TAG "UpscaleYUK_JNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

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

extern "C" JNIEXPORT jstring JNICALL
Java_com_upscaleyuk_app_MainActivity_stringFromJNI(
    JNIEnv* env,
    jobject /* this */) {

    // Verifikasi simbol NCNN & Vulkan ter-link dengan benar
    int gpu_count = ncnn::get_gpu_count();
    LOGI("NCNN Library successfully linked! Vulkan GPU count: %d", gpu_count);

    std::string info = "UpscaleYUK Ready | NCNN Linked | Vulkan GPUs: " + std::to_string(gpu_count);
    return env->NewStringUTF(info.c_str());
}

