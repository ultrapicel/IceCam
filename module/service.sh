#!/system/bin/sh
ICE=/data/adb/icecam
mkdir -p "$ICE/bin" "$ICE/logs" "$ICE/config" "$ICE/state"
cp -f /data/adb/modules/icecam/bin/icecamctl "$ICE/bin/icecamctl" 2>/dev/null
chmod 755 "$ICE/bin/icecamctl" 2>/dev/null
ln -sf "$ICE/bin/icecamctl" "$ICE/icecamctl" 2>/dev/null
echo "$(date '+%Y-%m-%d %H:%M:%S') service: IceCam v0.3.1.4-dev start" >> "$ICE/logs/module.log"
echo "sdk=$(getprop ro.build.version.sdk) device=$(getprop ro.product.device) abi=$(getprop ro.product.cpu.abi)" >> "$ICE/logs/module.log"
