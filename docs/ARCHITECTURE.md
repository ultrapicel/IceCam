# IceCam Architecture v2

IceCam v2 is still a development skeleton. It introduces the media preview canvas, camera profile dump, module state paths, and hook preparation commands.

## Components

- `app/`: Android APK control panel.
- `module/`: KernelSU/Magisk/APatch root module.
- `hooks/`: placeholder for LSPosed/Zygisk hook code.
- `native/`: placeholder for future NDK camera hook code.

## v2 status

Implemented:

- Root request from UI.
- Module status check through `icecamctl status`.
- Hook preparation state through `icecamctl prepare`.
- Media picker.
- SurfaceView preview renderer.
- Pan/zoom/rotate/mirror/Fit/Fill controls.
- CameraCharacteristics dump.
- Detailed logs and export bundle.

Not implemented yet:

- Real Camera2 frame injection.
- LSPosed runtime hooks.
- Zygisk native hooks.
- HAL/provider replacement.

