# IceCam v7.3 diagnostic bundle build

v7.3 keeps the v7.2 LSPosed hookAllMethods fix and improves Export Debug Bundle.

## Main purpose

This build is still log-only. It is designed to prove stable LSPosed loading and camera API telemetry before frame injection.

## Export Debug Bundle now collects

- `/data/adb/icecam/logs`, `config`, `state`, media listing
- `/data/adb/lspd/log` and LSPosed module/config traces when readable
- `logcat -d` full, filtered, crash buffer
- `dmesg` / `last_kmsg` when available
- `getprop`, `ps -A`, process dumps
- camera dumpsys/service info
- package dumps for IceCam, Camera, Chrome, Telegram
- installed APK path and `assets/xposed_init` verification when `unzip` is available

Output path:

`/sdcard/Download/icecam_debug_v7.3_<timestamp>.tar.gz`

## Install matrix

- APK: update
- Root module: reinstall, because `icecamctl logs` changed
- Reboot: required after module reinstall
- LSPosed scope: Camera, Chrome, Telegram, Telegram Web, target apps

## Expected hook.log after opening target camera

```text
[LOAD] package=...
[HOOKED] android.hardware.camera2.CameraManager.openCamera
[Camera2] getCameraIdList
[Camera2] getCameraCharacteristics id=...
[Camera2] openCamera ...
```
