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


## v3.1.3
GitHub Actions fixed: no local gradlew required; workflow installs Gradle 8.7 and locates the project root automatically.

## v3.1.3 fix
- Xposed API is vendored as a local compileOnly stub JAR for GitHub Actions.
- The stub JAR is only for compilation and is not packaged into the APK.
