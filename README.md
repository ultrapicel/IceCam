# IceCam v6 dev

Media preview + replacement state release.

For v6:

```text
APK: update
Root module: reinstall
Reboot: required
LSPosed scope: target apps
```

Added:
- Select Photo
- Select Video
- Image preview
- Video preview
- Start Replacement
- Stop Replacement
- active state: /data/adb/icecam/state/active
- working media copy: /data/adb/icecam/media/source
- hook logs active/mode/mediaPath when target opens camera

Limitation:
- v6 still does not inject frames into CameraDevice output. It prepares active media state and verifies hook interception.
