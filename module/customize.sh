ui_print "Installing IceCam v0.3.0-dev"
mkdir -p /data/adb/icecam/logs /data/adb/icecam/state /data/adb/icecam/config
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/service.sh 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm $MODPATH/common/icecamctl 0 0 0755
