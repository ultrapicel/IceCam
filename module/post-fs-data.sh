#!/system/bin/sh
LOG_DIR=/data/adb/icecam/logs
mkdir -p "$LOG_DIR"
echo "$(date '+%F %T') IceCam post-fs-data" >> "$LOG_DIR/module.log"
