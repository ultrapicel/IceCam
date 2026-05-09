#!/system/bin/sh
ui_print "Installing IceCam v7-from-scratch"
ICE=/data/adb/icecam
mkdir -p $ICE/bin $ICE/logs $ICE/state $ICE/config $ICE/media
cp -f $MODPATH/common/bin/icecamctl $ICE/bin/icecamctl 2>/dev/null
chmod 755 $ICE/bin/icecamctl
ln -sf $ICE/bin/icecamctl $ICE/icecamctl
$ICE/bin/icecamctl prepare-hooks >/dev/null 2>&1
ui_print "IceCam control path: /data/adb/icecam/bin/icecamctl"
