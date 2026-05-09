# IceCam v1.1 dev

IceCam is a research/development project for Android camera replacement on rooted devices.

## Build artifacts

GitHub Actions produces:

- `IceCam-app-v0.1.1-dev.apk`
- `IceCam-root-module-v0.1.1-dev.zip`
- `IceCam-source-snapshot.zip`

## Current v1 scope

This is a skeleton/dev base:

- Android control app
- camera diagnostics dump
- root check
- exportable logging path
- KernelSU/Magisk/APatch module layout
- LSPosed hook skeleton
- native hook placeholder

## Logs

App logs:

```sh
/sdcard/Download/IceCamLogs/icecam_app.log
```

Root module logs:

```sh
su -c icecamctl logs
```

The command creates:

```sh
/sdcard/Download/icecam_logs.tar.gz
```

## Planned hook targets

- Camera1: `android.hardware.Camera`
- Camera2: `android.hardware.camera2.CameraManager`
- CameraX wrappers
- NDK camera API
- lower camera service / provider path after MVP validation
