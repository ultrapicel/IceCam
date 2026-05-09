#!/system/bin/sh
ICE=/data/adb/icecam
mkdir -p $ICE/logs
echo "$(date '+%F %T') IceCam service started" >> $ICE/logs/module.log
