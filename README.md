# IceCam v8.0 profile-clone

v8.0 is a passive CameraCharacteristics profile clone/cache stage. It still does **not** inject frames.

Changes:
- Keeps v7.6 stable LSPosed hook baseline.
- Adds profile cache for real `CameraCharacteristics`.
- Saves latest profile to `/data/adb/icecam/cache/camera_profiles.json`.
- Appends profile events to `/data/adb/icecam/cache/camera_profiles.jsonl`.
- Adds compatibility profile modes: `strict-real`, `compatibility`, `experimental`.
- Adds UI buttons to read profile cache.
- Debug bundle includes `/data/adb/icecam/cache`.

Install/test:
1. Install APK.
2. Install root module ZIP.
3. Reboot.
4. Keep LSPosed scope: Camera, Telegram, Chrome, target apps.
5. Open IceCam -> Dashboard -> run steps 1..6.
6. Open target camera screens before exporting the bundle.

Expected markers:
`[LOAD]`, `[HOOKED]`, `getCameraIdList`, `getCameraCharacteristics`, `openCamera`, `[ProfileCache] saved id=...`.

Next stage after v8 passes: v9 media render pipeline.
