#!/system/bin/sh
ICE=/data/adb/icecam
MODDIR=${0%/*}
mkdir -p $ICE/bin $ICE/logs $ICE/state $ICE/config $ICE/media $ICE/cache
cp -f $MODDIR/common/bin/icecamctl $ICE/bin/icecamctl 2>/dev/null
chmod 755 $ICE/bin/icecamctl
ln -sf $ICE/bin/icecamctl $ICE/icecamctl
chmod 755 /data/adb 2>/dev/null
chmod 777 $ICE $ICE/logs $ICE/state $ICE/config $ICE/media $ICE/cache 2>/dev/null
touch $ICE/logs/module.log $ICE/logs/hook.log
chmod 666 $ICE/logs/module.log $ICE/logs/hook.log 2>/dev/null
$ICE/bin/icecamctl status >> $ICE/logs/module.log 2>&1
