#!/system/bin/sh
BASE=/data/adb/icecam
LOG_DIR=$BASE/logs
STATE_DIR=$BASE/state
mkdir -p "$LOG_DIR" "$STATE_DIR"
echo "$(date '+%F %T') post-fs-data: IceCam v0.2.0-dev init" >> "$LOG_DIR/module.log"
chmod -R 755 "$BASE"
