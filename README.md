# IceCam v9.2 — Surface Trace Prep

IceCam is an Android app + root module + LSPosed hook layer for research on camera API interception on rooted personal devices.

## Current stage

`v9.2-surface-trace` is still **passive telemetry only**. It does not inject frames and does not replace a real camera stream yet.

What is implemented:

- Android app UI with a guided 1→6 development flow.
- Root control layer at `/data/adb/icecam/bin/icecamctl`.
- LSPosed Camera1/Camera2 telemetry.
- CameraCharacteristics profile cache through logcat bridge.
- Capture session trace.
- Surface/ImageReader/SurfaceTexture trace for media renderer preparation.
- Lite debug bundle by default; full bundle only on explicit request.
- GitHub Actions build for APK, root module, and source snapshot.

## Required files

The project archive must contain:

- `app/`
- `module/`
- `xposed_stub_src/`
- `.github/workflows/build.yml`
- `settings.gradle`
- `build.gradle`
- `README.md`

No external Xposed Maven dependency is used. The workflow builds `app/libs/xposed-api-stub.jar` from `xposed_stub_src`.

## Build

Push this repository to GitHub and run **Build IceCam** from Actions.

Artifacts:

- `IceCam-app-v9.2.apk`
- `IceCam-root-module-v9.2.zip`
- `IceCam-source-snapshot-v9.2.zip`
- `build-info-v9.2.txt`

## Install / test matrix

| Component | Action |
|---|---|
| APK | update/install |
| Root module | reinstall |
| Reboot | required |
| LSPosed scope | Camera, Telegram, Chrome, target apps |

## Development flow

In the app, use Dashboard steps:

1. Request Root Check
2. Prepare Hook Layer
3. Select Photo/Video
4. Start Replacement
5. Open Target Camera
6. Export Lite Debug Bundle

The file to send back for analysis is:

`/sdcard/Download/icecam_debug_v9.2_<timestamp>.tar.gz`

## Lite debug bundle contents

- `summary.txt`
- `icecam/logs/hook.log`
- `icecam/logs/module.log`
- `icecam/config/app_config.json`
- `icecam/state/active`
- `icecam/state/prepared`
- `icecam/cache/camera_profiles_from_logcat.jsonl`
- `icecam/cache/capture_session_events_from_logcat.jsonl`
- `icecam/cache/surface_events_from_logcat.jsonl`
- `icecam/cache/hook_access_probe_from_logcat.txt`
- `media/source.meta.json`
- filtered logcat slices only

## Success criteria for v9.2

Expected telemetry:

- `[LOAD]`
- `[Camera2] getCameraIdList`
- `[Camera2] getCameraCharacteristics`
- `[Camera2] openCamera`
- `CaptureSessionJson`
- `SurfaceJson`

If these are present, the next step is `v9.3`: renderer-side source normalization and a controlled Surface mapping plan. Frame injection is still not enabled in v9.2.
