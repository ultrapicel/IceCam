#!/system/bin/sh
MODDIR=${0%/*}
mkdir -p /data/adb/icecam/logs /data/adb/icecam/state /data/adb/icecam/config
log=/data/adb/icecam/logs/module.log
echo "$(date '+%F %T') service: IceCam v0.3.0-dev start" >> $log
echo "sdk=$(getprop ro.build.version.sdk) device=$(getprop ro.product.device) abi=$(getprop ro.product.cpu.abi)" >> $log
cp -f "$MODDIR/common/icecamctl" /data/adb/icecam/icecamctl
chmod 0755 /data/adb/icecam/icecamctl
ln -sf /data/adb/icecam/icecamctl /system/bin/icecamctl 2>/dev/null || true
