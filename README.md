# IceCam v4 dev

IceCam v4 is a GitHub-ready Android + root module + LSPosed hook-layer skeleton.

## Build

Upload this repository to GitHub and run:

`Actions -> Build IceCam v4`

Artifacts:

- `IceCam-app-v0.4.0-dev.apk`
- `IceCam-root-module-v0.4.0-dev.zip`
- `IceCam-source-snapshot-v0.4.0-dev.zip`

## Install

For v4:

```text
APK: update
Root module: reinstall
Reboot: required
LSPosed scope: Camera / Telegram / Chrome
```

## Current behavior

v4 is log-only. It does not replace frames yet.

It should log:

- package load
- `CameraManager.getCameraIdList`
- `CameraManager.getCameraCharacteristics`
- `CameraManager.openCamera`
- legacy `android.hardware.Camera.open`

Main log:

```sh
su -c cat /data/adb/icecam/logs/hook.log
```

Logcat fallback:

```sh
logcat -d | grep 'IceCam/Hook'
```

## Control

```sh
su -c /data/adb/icecam/bin/icecamctl status
su -c /data/adb/icecam/bin/icecamctl prepare-hooks
su -c /data/adb/icecam/bin/icecamctl logs
```
