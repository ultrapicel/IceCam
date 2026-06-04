#include <jni.h>
#include "icecam_logger.h"

extern "C" {

JNIEXPORT void JNICALL
Java_dev_icecam_app_NativeBootstrap_initNativeLogger(JNIEnv* env, jobject thiz, jstring logPath) {
    const char* path = env->GetStringUTFChars(logPath, nullptr);
    icecam::FileLogger::getInstance().init(path);
    env->ReleaseStringUTFChars(logPath, path);

    LOGI("Native", "File logger initialized");
}

} // extern "C"