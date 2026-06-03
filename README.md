# IceCam v10 Report-Refined Reconstruction

GitHub-ready Android project.

## Build

Upload this repository to GitHub and run:

`Actions -> Android APK build`

The workflow uploads the debug APK as an artifact.

## What changed in v10

- English-only single-page UI.
- Native flow preserved from the working v8 build.
- Floating menu remapped for practical media control:
  - Zoom + / Zoom -
  - pan arrows
  - Center
  - Crop
  - Fit/Fill
  - Rotate 90
  - Mirror
  - Play / Stop
- Report-refined diagnostics.
- Strong TX24 logging.

## Notes

`TX24` in this project means Binder transaction code 24:

`TX24(mode, panX, panY, zoomX, zoomY, flags)`

It is not the same as RGB24/TX24 pixel-format conversion mentioned in some native reports.


## v10 notes

- App label: IceCam.
- Fixed daemon/Binder service name: `privsam_service`.
- Media switching uses safe sequence: TX25 reset -> TX22 range -> TX14 mode/source -> TX11 play -> TX24 transform.
- Random ServerName generation removed to prevent stale Binder handles after media changes.
