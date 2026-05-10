# IceCam v9.4.4 — Root Bootstrap Cleanup + Renderer Diagnostics

`v9.4.4-safe-storage-bootstrap` is still passive telemetry only. It does not inject frames and does not replace a camera stream yet.

## Added in v9.4.4

- Root bootstrap from app startup via `icecamctl bootstrap-app`.
- Root-side permission/appops grant attempt for `com.icecam.dev`.
- Install/start/prepare cleanup for stale v9.x temp/cache/debug artefacts.
- Persistent root bootstrap markers: `/data/adb/icecam/state/root_granted` and `root_granted_at`.
- v9.4.1 renderer diagnostics remain active: renderer thread, placeholder producer, owned SurfaceTexture telemetry.

## Still disabled

- fake frame injection
- Surface replacement
- virtual camera provider
- CameraDevice proxy replacement

## Expected GitHub Actions artifacts

- `IceCam-app-v9.4.4.apk`
- `IceCam-root-module-v9.4.4.zip`
- `IceCam-source-snapshot-v9.4.4.zip`

## Test flow

1. Install/update root module.
2. Install APK.
3. Open IceCam once; root should be requested at startup.
4. Press `Prepare Hook Layer`.
5. Select media.
6. Press `Start Replacement`.
7. Open Camera/Telegram/Chrome camera for 5–10 seconds.
8. Export Lite Debug Bundle.

Lite bundle path:

`/sdcard/Download/icecam_debug_v9.4.4_<timestamp>.tar.gz`
