# IceCam v8.1 logcat profile bridge

v8.1 keeps v8 passive CameraCharacteristics profiling, but changes diagnostics to handle SELinux/app-domain write restrictions.

Current state:
- LSPosed hook layer is working.
- Camera2 calls are intercepted.
- Some target app processes cannot write/read `/data/adb/icecam/*` directly even when chmod is permissive.
- Therefore hook telemetry and profile JSON are also emitted to logcat/LSPosed logs and collected by `icecamctl logs`.

Important files in debug bundle:
- `logcat/logcat_filtered.txt`
- `icecam/cache/camera_profiles_from_logcat.jsonl`
- `icecam/cache/hook_access_probe_from_logcat.txt`
- `icecam/permissions.txt`

v8.1 is still passive. It does not inject frames.
