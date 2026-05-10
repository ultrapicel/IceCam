#!/system/bin/sh
ui_print "Installing IceCam v9.4.2 root bootstrap cleanup"
ICE=/data/adb/icecam
mkdir -p $ICE/bin $ICE/logs $ICE/state $ICE/config $ICE/media $ICE/cache
cp -f $MODPATH/common/bin/icecamctl $ICE/bin/icecamctl 2>/dev/null
chmod 755 $ICE/bin/icecamctl
ln -sf $ICE/bin/icecamctl $ICE/icecamctl
chmod 755 /data/adb 2>/dev/null
chmod 777 $ICE $ICE/logs $ICE/state $ICE/config $ICE/media $ICE/cache 2>/dev/null
touch $ICE/logs/module.log $ICE/logs/hook.log
chmod 666 $ICE/logs/module.log $ICE/logs/hook.log 2>/dev/null
$ICE/bin/icecamctl bootstrap-app >/dev/null 2>&1
$ICE/bin/icecamctl prepare-hooks >/dev/null 2>&1
ui_print "IceCam control path: /data/adb/icecam/bin/icecamctl"
ui_print "Cleanup/bootstrap: enabled"
