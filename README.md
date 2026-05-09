# IceCam v7-from-scratch

Clean baseline for IceCam Android app + root module + LSPosed hook telemetry.

## v7 scope

- No automatic `su` call in `onCreate`.
- Root prompt only from explicit buttons.
- No frame injection yet.
- Default hook mode is `log-only`.
- UI has tabs: Dashboard, Root, Media, Hooks, Logs, Diagnostics.
- Root control path: `/data/adb/icecam/bin/icecamctl`.
- Symlink: `/data/adb/icecam/icecamctl`.
- Hook log: `/data/adb/icecam/logs/hook.log`.

## Build

Use GitHub Actions: `.github/workflows/build.yml`.

Artifacts:

- `IceCam-app-v7.apk`
- `IceCam-root-module-v7.zip`
- `IceCam-source-snapshot-v7.zip`
- `build-info.txt`

## Install / update matrix

For v7 test:

- APK: update/install `IceCam-app-v7.apk`.
- Root module: reinstall `IceCam-root-module-v7.zip`.
- Reboot: required after module install/reinstall.
- LSPosed scope: enable IceCam module and select Camera, Telegram, Chrome, and the target apps you test. Select IceCam itself only if LSPosed shows it and you need self-test logging.

## Expected v7 success criteria

1. APK installs and opens.
2. Root module installs.
3. Reboot completes.
4. Root -> Request Root Check triggers the root prompt and prints stdout/stderr/exitCode.
5. `/data/adb/icecam/bin/icecamctl status` works.
6. Media -> Select Photo/Video works.
7. Preview works with ImageView/VideoView.
8. Start Replacement writes:
   - `/data/adb/icecam/state/active = 1`
   - `/data/adb/icecam/media/source`
   - `/data/adb/icecam/config/app_config.json`
9. Export Debug Bundle writes `/sdcard/Download/icecam_debug_v7.tar.gz`.
10. LSPosed hook telemetry writes `[LOAD]`, `getCameraIdList`, `getCameraCharacteristics`, `openCamera` where supported by the target app.

## Troubleshooting

- If `[LOAD]` is missing: check LSPosed packaging, module enable state, and scope.
- If `[LOAD]` exists but `openCamera` is missing: target app may use another camera path or the openCamera overload coverage is incomplete.
- If `openCamera` exists: v7 baseline is good enough to start v8/v9 planning.
