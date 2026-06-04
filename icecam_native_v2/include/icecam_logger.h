#pragma once

#include <string>
#include <android/log.h>
#include <cstdio>
#include <mutex>

namespace icecam {

enum class LogLevel {
    DEBUG = 0,
    INFO,
    WARN,
    ERROR
};

class FileLogger {
public:
    static FileLogger& getInstance();

    void init(const std::string& logFilePath);
    void log(LogLevel level, const char* tag, const char* fmt, ...);
    void flush();
    std::string getLogFilePath() const;

private:
    FileLogger() = default;
    ~FileLogger();

    std::mutex mutex_;
    FILE* file_ = nullptr;
    std::string logPath_;
    bool initialized_ = false;
};

} // namespace icecam

// Convenience macros
#define LOGD(tag, ...) icecam::FileLogger::getInstance().log(icecam::LogLevel::DEBUG, tag, __VA_ARGS__)
#define LOGI(tag, ...) icecam::FileLogger::getInstance().log(icecam::LogLevel::INFO,  tag, __VA_ARGS__)
#define LOGW(tag, ...) icecam::FileLogger::getInstance().log(icecam::LogLevel::WARN,  tag, __VA_ARGS__)
#define LOGE(tag, ...) icecam::FileLogger::getInstance().log(icecam::LogLevel::ERROR, tag, __VA_ARGS__)