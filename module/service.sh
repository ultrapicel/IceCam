#!/system/bin/sh
LOG_DIR=/data/adb/icecam/logs
mkdir -p "$LOG_DIR"
echo "$(date '+%F %T') IceCam service start" >> "$LOG_DIR/module.log"
chmod -R 755 /data/adb/icecam
