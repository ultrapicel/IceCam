# IceCam v9.6.4 — Single Preview Surface Renderer

`v9.6.4-single-preview-surface-renderer` continues the Camera2 Surface/CaptureSession path from v9.6.3, but changes the renderer from aggressive multi-surface painting to a conservative single-preview target model.

## Goal

IceCam is still aimed at universal Android camera replacement, not a Telegram/Chrome-specific hook. The LSPosed layer remains a diagnostic/fallback layer while the long-term direction is a lower-level system camera/provider/HAL pipeline.

## Added in v9.6.4

- Single active preview Surface renderer per process.
- Surface scoring before rendering.
- ImageReader/Chrome/WebRTC surfaces stay deny/log-only.
- 450 ms debounce after Camera2 `addTarget` discovery before selecting the final preview Surface.
- Existing renderer is stopped when a better preview target is selected.
- Fail-safe stop on first lock/post/render exception to avoid camera disconnect loops.
- Provider `media-source` stream proxy so target apps decode media through IceCam provider instead of direct SAF URI.
- First image renderer path for selected SAF image media:
  - center-crop/fill transform;
  - zoom;
  - mirror;
  - rotation;
  - fallback to animated test pattern when image media is unavailable.
- New logs:
  - `SurfaceClassifierJson` includes `score`;
  - `SurfaceSelectJson` shows debounce/candidate/selected events;
  - `Camera2SurfaceRenderJson` shows selected renderer lifecycle.

## Still not complete

- Stable video decoding through MediaCodec.
- True Camera2 frame replacement without HAL conflict.
- Telegram render path stabilization.
- System camera ID/provider/HAL replacement.
- Vendor provider shim.

## GitHub Actions artifacts

- `IceCam-app-v9.6.4.apk`
- `IceCam-root-module-v9.6.4.zip`
- `IceCam-source-snapshot-v9.6.4.zip`

## Test flow

1. Install APK and root module.
2. Reboot after LSPosed/module update.
3. Open IceCam and select an image through SAF.
4. Enable Auto Pipeline / replacement mode.
5. Test first in system Camera app, then Telegram, then Chrome/WebRTC.
6. Export Lite Debug Bundle.
7. Send `icecam_debug_v9.6.4_<timestamp>.tar.gz` plus screenshots.

## Required regression checks

- App must not request `MANAGE_EXTERNAL_STORAGE`.
- App must not use storage appops/self `pm grant` bootstrap.
- Hook layer must not call `XposedHelpers.findAndHookMethod(...)`.
- Target apps must use provider bridge instead of direct `/data/adb` reads/writes.
