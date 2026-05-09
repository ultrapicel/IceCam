#!/system/bin/sh
ICE=/data/adb/icecam
mkdir -p "$ICE/bin" "$ICE/logs" "$ICE/state" "$ICE/config" "$ICE/media"
chmod 755 "$ICE" "$ICE/bin" "$ICE/state" "$ICE/config" "$ICE/media"
chmod 777 "$ICE/logs"
echo "$(date '+%F %T') post-fs-data: IceCam v0.6.1-dev init" >> "$ICE/logs/module.log"
