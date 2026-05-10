# IceCam project context

Current version: v9.4.

Purpose: Android app + root module + LSPosed hook layer for broad camera-stack interception on Android 12-15. Goal is broad compatibility with most apps using Camera1, Camera2, CameraX, WebRTC/WebView and later NDK camera path, not only Telegram/Chrome/Camera. Telegram/Chrome/Camera are test targets.

Current status:
- LSPosed module loading works.
- Camera1/Camera2 hooks work.
- openCamera/getCameraIdList/getCameraCharacteristics are traced.
- createCaptureSession, CaptureRequest.Builder, OutputConfiguration, Surface, ImageReader traces are implemented.
- Root module creates /data/adb/icecam with logs/config/cache/state/media.
- icecamctl supports status, prepare-hooks, start, stop, logs, full-logs, clear-logs, reset-dev-data.
- UI has glass-style diagnostic flow, prefs persistence, startup root check.
- Debug bundle has lite/full modes.

Known not implemented yet:
- real frame replacement
- Surface replacement
- OpenGL/MediaCodec renderer
- virtual camera provider
- stealth release mode

Current step implemented: v9.4 renderer sandbox. Prepared internal renderer thread, placeholder frame producer, SurfaceTexture ownership tracking, and renderer telemetry. Do not yet claim working camera replacement.

Important constraints:
- GitHub ZIP must include .github/workflows/build.yml at repository root.
- Do not use de.robv.android.xposed:api:82.
- Use local xposed-api-stub.jar / xposed_stub_src only.
- Do not use XposedHelpers.findAndHookMethod due LSPosed runtime mismatch. Use XposedBridge.hookAllMethods.
- Keep debug bundle compact; full logs only in full debug mode.
- Filter noisy events such as com.icecam.dev and Surface.isValid spam.
