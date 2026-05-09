#!/system/bin/sh
ICE=/data/adb/icecam
mkdir -p "$ICE/bin" "$ICE/logs" "$ICE/config" "$ICE/state"
cp -f /data/adb/modules/icecam/bin/icecamctl "$ICE/bin/icecamctl" 2>/dev/null
chmod 755 "$ICE/bin/icecamctl" 2>/dev/null
ln -sf "$ICE/bin/icecamctl" "$ICE/icecamctl" 2>/dev/null
chmod 755 "$ICE" "$ICE/bin" "$ICE/logs" "$ICE/config" "$ICE/state" 2>/dev/null
echo "$(date '+%Y-%m-%d %H:%M:%S') post-fs-data: IceCam v0.3.1.5-dev init" >> "$ICE/logs/module.log"
