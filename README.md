# IceCam Core v2.0 - Modern Camera Replacement

Modernized version of IceCam with updated native layer using **ShadowHook v2.0.0** and better compatibility with Android 14/15/16.

## Features (Planned / In Progress)

- Updated hooking using ShadowHook v2.0.0
- Support for AHardwareBuffer (modern path) + fallback to GraphicBuffer
- On-the-fly source switching (M1-M4)
- File-based logging (easy to share)
- Clean architecture ready for GPU transformations

## Project Structure

- `icecam_native_v2/` — New native layer (CMake + ShadowHook v2.0.0)
- `app/` — Java/Kotlin application layer
- `.github/workflows/build.yml` — Ready-to-use GitHub Actions

## How to Build

### Local Build

```bash
./gradlew assembleDebug
```

### GitHub Actions

Push to `main` or `master` branch — the workflow will automatically build Debug and Release APKs.

## Logging

The app writes detailed logs to a file inside internal storage.
You can share the log file directly from the app for analysis.

## Next Steps

This is the foundation. The following will be added in iterations:
- Full implementation of frame capture hooks
- GPU transformation engine
- Improved realtime preview
- Stable on-the-fly source switching

## Credits

Based on the original working implementation + modernized with ShadowHook v2.0.0.