#include "icecam_logger.h"
#include <string>

namespace icecam {

void startFrameCapture(const std::string& sourceId) {
    LOGI("Capture", "Starting frame capture for source: %s", sourceId.c_str());
    // TODO: Implement actual capture using AHardwareBuffer or GraphicBuffer
}

void stopFrameCapture() {
    LOGI("Capture", "Stopping frame capture");
}

void switchSource(const std::string& newSourceId) {
    LOGI("Capture", "Switching to source on the fly: %s", newSourceId.c_str());
    // TODO: Implement seamless source switching with frame buffering
}

} // namespace icecam