# IceCam v9.6.2 — Continuous Surface Renderer

`v9.6.2-continuous-surface-renderer` pivots IceCam toward a system-level camera-provider/HAL route. It is still experimental continuous Surface renderer enabled under auto-pipeline; provider replacement is not enabled.

## Goal

Stop relying on per-app behavior as the primary architecture. LSPosed remains useful for telemetry and fallback, but the main target is a system-camera path that can affect most apps through Android's normal camera stack.

## Added in v9.6.2

- Root-side `system-probe` command.
- Debug bundle now includes `system_camera_probe/`.
- Camera provider/HAL inventory:
  - `dumpsys media.camera`
  - `service list`
  - `lshal` camera entries
  - `/vendor/bin/hw/*camera*`
  - `/vendor/lib*/hw/*camera*`
  - VINTF manifest camera references
  - external camera config discovery
  - `/dev/video*`, `/dev/media*`, `/sys/class/video4linux`
  - kernel config probe for V4L2/UVC/v4l2loopback
  - SELinux camera/provider contexts and AVC denials
- UI button: Root → Continuous Surface Renderer.

## Architectural direction

Priority order:

1. External camera/provider emulation if device exposes a usable provider path.
2. V4L2/UVC-compatible route if kernel/vendor supports it.
3. Vendor/AIDL camera provider feasibility on Android 14+.
4. LSPosed Camera2/Camera1 Surface/CaptureSession interception as fallback, not final design.

## GitHub Actions artifacts

- `IceCam-app-v9.6.2.apk`
- `IceCam-root-module-v9.6.2.zip`
- `IceCam-source-snapshot-v9.6.2.zip`

## Test flow

1. Install APK and root module.
2. Reboot after LSPosed/module update.
3. Open IceCam.
4. Root → Continuous Surface Renderer, or Dashboard → normal diagnostic flow.
5. Export Lite Debug Bundle.
6. Send `icecam_debug_v9.6.2_<timestamp>.tar.gz`.
