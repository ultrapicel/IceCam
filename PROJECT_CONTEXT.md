# IceCam Project Context

Current version: v9.5.0-system-camera-probe.

Main direction: system-level camera replacement. LSPosed hooks are telemetry/fallback; the preferred future route is camera-provider/HAL/V4L2 feasibility.

v9.5.0 does not inject frames. It adds root diagnostics to determine which low-level route is viable on the test device and across Android 12-15:

- external camera provider route;
- V4L2/UVC/v4l2loopback route;
- AIDL/HIDL camera provider route;
- vendor camera provider replacement feasibility;
- SELinux/VINTF blockers.

Do not regress the working pieces from v9.4.6:

- app starts without storage permission crash;
- provider bridge works;
- renderer sandbox logs;
- Camera1/Camera2 hooks remain diagnostic;
- no direct `/data/adb` I/O from target apps.
