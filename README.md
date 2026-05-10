# IceCam v9.4 — Renderer Sandbox

`v9.4-renderer-sandbox` is still **passive telemetry only**. It does not inject frames and does not replace a camera stream yet.

This build adds the first internal renderer sandbox layer while preserving the v9.3.3 camera/surface tracing path.

## Added in v9.4

- Passive `RendererSandbox` class.
- Internal `HandlerThread` named `IceCamRendererSandbox`.
- Owned `SurfaceTexture` + owned `Surface` allocation.
- Placeholder `Bitmap` producer metadata.
- 30 fps timing tick telemetry.
- New cache file: `/data/adb/icecam/cache/renderer_events.jsonl`.
- Debug bundle now includes renderer events and renderer counters.

Renderer startup is gated behind camera activity and `/data/adb/icecam/state/active=1`; it is not started for every loaded process.

## Still not implemented

- Fake frame injection.
- Target Surface replacement.
- OpenGL compositing.
- MediaCodec decode/render pipeline.
- Virtual camera provider.
- Native/NDK camera path.

## GitHub Actions artifacts

The workflow produces:

- `IceCam-app-v9.4.apk`
- `IceCam-root-module-v9.4.zip`
- `IceCam-source-snapshot-v9.4.zip`
- `build-info.txt`

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
5. Open target camera manually: Camera / Telegram / Chrome / another target app
6. Export Lite Debug Bundle

Expected output:

`/sdcard/Download/icecam_debug_v9.4_<timestamp>.tar.gz`

Important files inside:

- `summary.txt`
- `icecam/logs/hook.log`
- `icecam/cache/renderer_events.jsonl`
- `icecam/cache/camera_profiles_from_logcat.jsonl`
- `icecam/cache/capture_session_events_from_logcat.jsonl`
- `icecam/cache/surface_events_from_logcat.jsonl`
- `icecam/cache/surface_ownership_from_logcat.jsonl`
- `media/source.meta.json`
- `logcat/icecam_camera_lsposed_filtered.txt`

## Success criteria for v9.4

The bundle should show:

- `[LOAD]`
- `openCamera`
- `createCaptureSession`
- `SurfaceOwnerJson`
- `CaptureRequest.Builder.addTarget` and/or `requestTargets`
- `RendererSandbox` init/tick events
- `renderer_event_count` greater than zero in `summary.txt`

If these are present, the next stage is v9.5 experimental Surface injection. Frame injection remains disabled in v9.4.
