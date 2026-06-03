# IceCam MaxRecon v8

Clean-room Android multimedia control reconstruction based on recovered APK/DEX/native reports.

## Build on GitHub Actions

Upload this repository and run:

```text
Actions → Android APK build
```

The debug APK will be uploaded as an artifact.

## v8 focus

- English UI
- Gallery photo/video picker
- root bootstrap
- native file deployment
- `/data/vcplax <ServerName>` launch
- Binder diagnostics
- TX11/TX14 source control
- TX24 transform control
- TX25 stop
- floating 4x4 transform panel
- Share log / Full status

## Floating controls

```text
Zoom +     Up       Zoom -     Fit/Fill
Left       Center   Right      Reset
Play       Down     Loop       Status
Rotate     Mirror   Stop       Close
```

## TX24 layout

```text
TX24(mode, panX, panY, zoomX, zoomY, flags)
```

This is a compatibility reconstruction, not a 1:1 source recovery.
