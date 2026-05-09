#!/system/bin/sh
ICE=/data/adb/icecam
MODDIR=${0%/*}
mkdir -p "$ICE/bin" "$ICE/logs" "$ICE/state" "$ICE/config" "$ICE/media"
if [ -f "$MODDIR/common/bin/icecamctl" ]; then
  cp -f "$MODDIR/common/bin/icecamctl" "$ICE/bin/icecamctl"
  chmod 755 "$ICE/bin/icecamctl"
  ln -sf "$ICE/bin/icecamctl" "$ICE/icecamctl"
fi
chmod 777 "$ICE/logs"
echo "$(date '+%F %T') service: IceCam v0.6.0-dev start" >> "$ICE/logs/module.log"
echo "sdk=$(getprop ro.build.version.sdk) device=$(getprop ro.product.device) abi=$(getprop ro.product.cpu.abi)" >> "$ICE/logs/module.log"
