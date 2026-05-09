#!/system/bin/sh
ICE=/data/adb/icecam
MODDIR=${0%/*}
mkdir -p $ICE/bin $ICE/logs $ICE/state $ICE/config $ICE/media
cp -f $MODDIR/common/bin/icecamctl $ICE/bin/icecamctl 2>/dev/null
chmod 755 $ICE/bin/icecamctl
ln -sf $ICE/bin/icecamctl $ICE/icecamctl
$ICE/bin/icecamctl status >> $ICE/logs/module.log 2>&1
