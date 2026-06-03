# IceCam Core v20

Diagnostic safe-transform build.

## v20 focus
- Transform buttons are state-only by default; backend replay happens only via **Apply now**.
- Prevents native stream collapse caused by JPEG bake/replay on every tap.
- Adds expanded diagnostic export: timestamps, sequence IDs, thread names, heap usage, prefs, file inventory, Binder state, root/service state, vcplax logs and filtered logcat.
- Keeps legacy TX14 -> TX11 path for explicit apply/replay.

Build through GitHub Actions using `.github/workflows/android.yml`.
