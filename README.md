# IceCam Core v17

GitHub-ready Android project.

Main changes in this build:

- M1–M4 media slots.
- Coalesced transform apply to prevent backend overload.
- Full floating controls.
- Cleaner high-quality image transform replay.
- Serialized backend operations.

Build with GitHub Actions: **Android APK build**.

# IceCam Core v15

Clean IceCam rebuild using the best recovered parts of the original APK without keeping the old UI flow.

## What is included

- Stable app name: **IceCam**
- Stable native service name: **privsam_service**
- Legacy native backend deployment: `libvc.so`, `libshadowhook.so`, `vcplax.so`
- Safe start / restore lifecycle
- Media picker for photo/video
- Floating controller
- TransformState model: zoom, pan, crop, rotate, mirror, fit/fill
- Photo transform engine: bakes a transformed 640x480 JPEG and replays it through the working native backend
- TX24 isolated as color-correction/debug only
- GitHub Actions Android APK build

## Current backend behavior

The recovered native backend reliably accepts local source playback through the legacy TX14 -> TX11 path. TX24 is not used for geometry because runtime tests showed it changes color/correction state.

## Next native stage

Realtime video zoom/pan/crop requires a clean renderer stage before the native backend: decoder -> GLES transform -> encoder/local stream/source bridge. This project is structured for that, while keeping the currently working replacement backend intact.
