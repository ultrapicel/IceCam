#include "icecam_logger.h"
#include <android/log.h>
#include <ctime>
#include <cstdarg>
#include <sys/stat.h>

namespace icecam {

FileLogger& FileLogger::getInstance() {
    static FileLogger instance;
    return instance;
}

void FileLogger::init(const std::string& logFilePath) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (initialized_) return;

    logPath_ = logFilePath;

    // Create parent directories if needed
    size_t lastSlash = logPath_.find_last_of('/');
    if (lastSlash != std::string::npos) {
        std::string dir = logPath_.substr(0, lastSlash);
        mkdir(dir.c_str(), 0777);
    }

    file_ = fopen(logPath_.c_str(), "a");
    if (file_) {
        initialized_ = true;
        fprintf(file_, "\n=== IceCam Native v2.0 started ===\n");
        fflush(file_);
    }
}

void FileLogger::log(LogLevel level, const char* tag, const char* fmt, ...) {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!initialized_ || !file_) return;

    char timeStr[32];
    time_t now = time(nullptr);
    struct tm* tm_info = localtime(&now);
    strftime(timeStr, sizeof(timeStr), "%Y-%m-%d %H:%M:%S", tm_info);

    const char* levelStr;
    switch (level) {
        case LogLevel::DEBUG: levelStr = "D"; break;
        case LogLevel::INFO:  levelStr = "I"; break;
        case LogLevel::WARN:  levelStr = "W"; break;
        case LogLevel::ERROR: levelStr = "E"; break;
        default: levelStr = "?";
    }

    // Write to file
    fprintf(file_, "[%s] [%s] [%s] ", timeStr, levelStr, tag);

    va_list args;
    va_start(args, fmt);
    vfprintf(file_, fmt, args);
    va_end(args);

    fprintf(file_, "\n");
    fflush(file_);

    // Also print to logcat for convenience
    va_list args2;
    va_start(args2, fmt);
    __android_log_vprint(ANDROID_LOG_INFO, "IceCamNative", fmt, args2);
    va_end(args2);
}

void FileLogger::flush() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (file_) fflush(file_);
}

std::string FileLogger::getLogFilePath() const {
    return logPath_;
}

FileLogger::~FileLogger() {
    if (file_) {
        fclose(file_);
    }
}

} // namespace icecam