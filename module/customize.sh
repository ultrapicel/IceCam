#!/system/bin/sh
ui_print "Installing IceCam v7.6 dev flow perms ui"
ICE=/data/adb/icecam
mkdir -p $ICE/bin $ICE/logs $ICE/state $ICE/config $ICE/media
cp -f $MODPATH/common/bin/icecamctl $ICE/bin/icecamctl 2>/dev/null
chmod 755 $ICE/bin/icecamctl
ln -sf $ICE/bin/icecamctl $ICE/icecamctl
chmod 755 /data/adb 2>/dev/null
chmod 777 $ICE $ICE/logs $ICE/state $ICE/config $ICE/media 2>/dev/null
touch $ICE/logs/module.log $ICE/logs/hook.log
chmod 666 $ICE/logs/module.log $ICE/logs/hook.log 2>/dev/null
$ICE/bin/icecamctl prepare-hooks >/dev/null 2>&1
ui_print "IceCam control path: /data/adb/icecam/bin/icecamctl"
