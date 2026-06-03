# IceCam Core v22

Unified floating control-plane build.

## v22 focus

- Floating controls no longer call Binder/TX or bake/replay independently.
- Added process-wide `TransformController`.
- MainActivity and FloatService route transform commands through the same control path.
- Floating controls are state-only by default; `Commit` uses the same controller path as the app button.
- MainActivity transform buttons still auto-commit through the unified controller.
- Legacy backend replay remains serialized by `BackendApplyQueue` using TX14 -> TX11 only.
- Stable output canvas from v21 is preserved to avoid resolution flips during rotate.

Build through GitHub Actions using `.github/workflows/android.yml`.
