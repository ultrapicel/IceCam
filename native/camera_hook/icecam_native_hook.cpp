#include <android/log.h>
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "IceCam/NDK", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "IceCam/NDK", __VA_ARGS__)

extern "C" int icecam_native_init() {
    LOGD("native hook placeholder init");
    return 0;
}
