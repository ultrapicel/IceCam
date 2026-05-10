# IceCam v9.3.1 — Surface Ownership Mapping

`v9.3.1-surface-ownership-buildfix` is still **passive telemetry only**. It does not inject frames and does not replace a camera stream yet.

This build moves from basic Surface tracing to ownership mapping:

- `CameraManager` / `CameraDevice` / `CameraCaptureSession` hooks remain active.
- `CaptureRequest.Builder.addTarget/removeTarget/build` is traced.
- `OutputConfiguration.addSurface/removeSurface/getSurfaces` is traced.
- Surface ownership events are exported through logcat bridge.
- Lite debug bundle stays small and includes only required development logs.

## GitHub Actions artifacts

The workflow produces:

- `IceCam-app-v9.3.1.apk`
- `IceCam-root-module-v9.3.1.zip`
- `IceCam-source-snapshot-v9.3.1.zip`
- `build-info-v9.3.txt`

## Install / test matrix

| Component | Action |
|---|---|
| APK | Update/install |
| Root module | Reinstall |
| Reboot | Required |
| LSPosed scope | Camera, Telegram, Chrome, target apps |

## Diagnostic flow

Use Dashboard steps:

1. Root Check
2. Prepare Hook Layer
3. Select Photo or Video
4. Start Replacement
5. Open target camera manually: Camera / Telegram / Chrome
6. Export Lite Debug Bundle

Expected output:

`/sdcard/Download/icecam_debug_v9.3_<timestamp>.tar.gz`

Important files inside:

- `summary.txt`
- `icecam/logs/hook.log`
- `icecam/cache/camera_profiles_from_logcat.jsonl`
- `icecam/cache/capture_session_events_from_logcat.jsonl`
- `icecam/cache/surface_events_from_logcat.jsonl`
- `icecam/cache/surface_ownership_from_logcat.jsonl`
- `media/source.meta.json`
- `logcat/icecam_camera_lsposed_filtered.txt`

## Success criteria for v9.3

The bundle should show:

- `[LOAD]`
- `openCamera`
- `getCameraCharacteristics`
- `createCaptureSession`
- `setRepeatingRequest` or `capture`
- `SurfaceJson`
- `SurfaceOwnerJson`
- `CaptureRequest.Builder.addTarget` and/or `requestTargets`

If these are present, the next stage is v9.4: internal renderer sandbox and placeholder frame producer. Frame injection remains disabled in v9.3.
