# IceCam v3 dev

Dev build with first LSPosed hook-layer skeleton.

Install instructions for v3:

- APK: update
- Root module: reinstall
- Reboot: required
- LSPosed: enable IceCam module and select target apps, then force-stop target app

v3 hook mode is **log-only**. It does not yet replace frames. It logs Camera2 calls:

- CameraManager.getCameraIdList
- CameraManager.getCameraCharacteristics
- CameraManager.openCamera

Logs:

```sh
su -c icecamctl logs
```

Expected bundle:

```text
/sdcard/Download/icecam_logs.tar.gz
```
