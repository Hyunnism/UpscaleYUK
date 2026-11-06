cmake_minimum_required(VERSION 3.22.1)
project("upscaleyuk")

# 1. Lokasi prebuilt NCNN arm64-v8a
set(ncnn_DIR ${CMAKE_CURRENT_SOURCE_DIR}/ncnn-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# 2. Shared library proyek
add_library(${CMAKE_PROJECT_NAME} SHARED
    native-lib.cpp
)

# 3. Link NCNN, Vulkan API, Bitmap JNI, dan Android System Libs
target_link_libraries(${CMAKE_PROJECT_NAME}
    ncnn
    vulkan
    jnigraphics
    android
    log
)

