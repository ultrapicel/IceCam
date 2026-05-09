# IceCam v5 dev

Large dev slice for IceCam.

## For v5

```text
APK: update
Root module: reinstall
Reboot: required
LSPosed scope: Camera / Telegram / Chrome / target apps
```

## What v5 includes

- Android app UI
- media picker
- preview image canvas
- transform config: mirror / zoom / rotate
- hook modes:
  - log-only
  - block-open-test
  - virtual-stub
- root module
- icecamctl
- LSPosed legacy hook entry
- Camera1/Camera2 hook telemetry
- app config path:
  `/data/adb/icecam/config/app_config.json`
- logs:
  `/data/adb/icecam/logs/hook.log`

## Current limitation

v5 still does not replace camera frames. It prepares the control/config/hook contract and lets us verify that target apps are intercepted.

Frame replacement requires the next layer: Surface/CameraDevice callback proxy or native camera path.
