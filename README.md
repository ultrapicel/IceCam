# IceCam Core v23

Ready-to-upload Android project for GitHub Actions.

## v23 focus

`v23-neon-control-panel` rebuilds the app-side control plane and UI around one source of truth:

- `BuildInfo` centralizes version labels for UI/logs/diagnostics.
- `TransformController` is the only owner of transform commands from MainActivity and FloatService.
- Main UI is preview-first: transforms update the preview immediately, then legacy backend apply is debounced.
- Floating controls use the same controller path and no longer own Binder/TX/bake logic.
- Start and Restore are merged into one state-aware button.
- Status panel shows backend/replacement/transform/source state.
- Media slots are displayed as thumbnail cards with `+` replace buttons.
- Buttons use neon pressed/selected states for touch feedback.

Legacy backend path remains `TX14 -> TX11`; `TX24` is still not used for geometry transform and `TX25` remains reserved for hard recovery only.
