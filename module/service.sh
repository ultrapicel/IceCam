#!/system/bin/sh
ICE=/data/adb/icecam
mkdir -p $ICE/bin $ICE/logs $ICE/state $ICE/config $ICE/media $ICE/cache
chmod 755 /data/adb 2>/dev/null
chmod 777 $ICE $ICE/logs $ICE/state $ICE/config $ICE/media $ICE/cache 2>/dev/null
chmod 666 $ICE/logs/module.log $ICE/logs/hook.log $ICE/state/active $ICE/state/prepared $ICE/config/app_config.json 2>/dev/null
echo "$(date '+%F %T') IceCam service started v9.3.2" >> $ICE/logs/module.log
/data/adb/icecam/bin/icecamctl status >> $ICE/logs/module.log 2>&1
