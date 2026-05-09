#!/system/bin/sh
mkdir -p /data/adb/icecam/logs /data/adb/icecam/state /data/adb/icecam/config
echo "$(date '+%F %T') post-fs-data: IceCam init" >> /data/adb/icecam/logs/module.log
