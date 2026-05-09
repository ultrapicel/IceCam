#!/system/bin/sh
BASE=/data/adb/icecam
LOG_DIR=$BASE/logs
STATE_DIR=$BASE/state
mkdir -p "$LOG_DIR" "$STATE_DIR"
echo "$(date '+%F %T') service: IceCam v0.2.0-dev start" >> "$LOG_DIR/module.log"
{
  echo "sdk=$(getprop ro.build.version.sdk)"
  echo "release=$(getprop ro.build.version.release)"
  echo "device=$(getprop ro.product.device)"
  echo "model=$(getprop ro.product.model)"
  echo "manufacturer=$(getprop ro.product.manufacturer)"
  echo "abi=$(getprop ro.product.cpu.abi)"
} > "$STATE_DIR/device.properties"
# This is preparation only. Real injection/hook activation is added in later dev builds.
echo "prepared=0" > "$STATE_DIR/hook_state"
chmod -R 755 "$BASE"
