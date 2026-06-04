cmake_minimum_required(VERSION 3.22.1)

project(icecam_native_v2)

# Enable C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Find Android libraries
find_library(log-lib log)
find_library(android-lib android)

# === ShadowHook v2.0.0 via Prefab (recommended) ===
# Add this to your app/build.gradle:
# implementation 'com.bytedance.android:shadowhook:2.0.0'
find_package(shadowhook REQUIRED CONFIG)

add_library(icecam_native_v2 SHARED
    src/hook_bootstrap.cpp
    src/frame_capture.cpp
    src/logger.cpp
    src/icecam_jni.cpp
)

target_include_directories(icecam_native_v2 PRIVATE include)

target_link_libraries(icecam_native_v2
    ${log-lib}
    ${android-lib}
    shadowhook::shadowhook
)

# Enable logging in debug builds
target_compile_definitions(icecam_native_v2 PRIVATE
    $<$<CONFIG:Debug>:ICECAM_ENABLE_DETAILED_LOG>
)