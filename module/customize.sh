#!/system/bin/sh
ui_print "Installing IceCam v0.3.1.4-dev"
set_perm_recursive $MODPATH 0 0 0755 0644
set_perm $MODPATH/bin/icecamctl 0 0 0755
set_perm $MODPATH/post-fs-data.sh 0 0 0755
set_perm $MODPATH/service.sh 0 0 0755
