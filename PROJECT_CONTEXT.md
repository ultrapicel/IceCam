# IceCam Project Context

Current version: v9.4.3.

Goal: Android app + root module + LSPosed hook layer for broad camera-stack telemetry and future system camera stream replacement on Android 12–15.

Current implementation is passive. It does not inject frames yet.

v9.4.3 adds root startup/bootstrap reliability and cleanup:

- app startup runs `icecamctl bootstrap-app` through `su`;
- root module cleans stale v9.x debug/cache/temp files on install/prepare/start;
- app permission/appops bootstrap is attempted from root;
- root markers are persisted in `/data/adb/icecam/state/root_granted*`;
- renderer sandbox diagnostics from v9.4.1 remain enabled.

Next stage after verifying logs: v9.5 experimental Surface injection. Do not proceed until renderer logcat events, root bootstrap markers, target LOAD/openCamera/createCaptureSession, and clean debug bundle counters are confirmed.
