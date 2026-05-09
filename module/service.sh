#!/system/bin/sh
ICE=/data/adb/icecam
mkdir -p $ICE/logs
chmod 777 $ICE/logs 2>/dev/null
echo "$(date '+%F %T') IceCam service started v7.3" >> $ICE/logs/module.log
/data/adb/icecam/bin/icecamctl status >> $ICE/logs/module.log 2>&1
