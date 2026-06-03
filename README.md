# IceCam v9 Report-Refined Reconstruction

GitHub-ready Android project.

## Build

Upload this repository to GitHub and run:

`Actions -> Android APK build`

The workflow uploads the debug APK as an artifact.

## What changed in v9

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
