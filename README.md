# IceCam v3.1 dev

Fixes root module layout. `icecamctl` is now installed at:

```sh
/data/adb/icecam/bin/icecamctl
```

Update matrix:

```text
APK: update
Root module: reinstall
Reboot: required
LSPosed: enable IceCam for target camera apps
```

v3.1 is log-only hook verification. It logs Camera2 calls; it does not replace frames yet.
