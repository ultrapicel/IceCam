# IceCam Project Context

Current version: v9.6.5-immediate-single-preview-renderer.

Main direction: universal system-level Android camera replacement. LSPosed hooks are telemetry/fallback; the preferred future route remains Camera2/CaptureSession/Surface path first, then camera-provider/HAL/vendor shim feasibility.

v9.6.5 changes the previous v9.6.3 bounded renderer into a safer single-preview-surface renderer:

- classify Camera2 target surfaces;
- deny ImageReader/Chrome/WebRTC surfaces;
- score preview candidates;
- debounce candidate selection for 450 ms;
- run only one active renderer per target process;
- stop immediately on lock/post/render exception;
- draw selected image media with center-crop, zoom, mirror and rotation when available;
- fallback to animated test pattern.

Do not regress:

- app starts without storage permission crash;
- no MANAGE_EXTERNAL_STORAGE/storage appops/self pm grant bootstrap;
- provider bridge works;
- target apps do not directly read/write `/data/adb`;
- Camera1/Camera2 tracing remains diagnostic;
- `XposedBridge.hookAllMethods(...)` is used instead of `XposedHelpers.findAndHookMethod(...)`.
