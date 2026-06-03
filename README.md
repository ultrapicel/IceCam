# IceCam Core v19

Android project for GitHub Actions builds.

## v19 focus

- Stable process-wide backend apply queue.
- Immutable apply requests instead of `pendingApplyPath`/`pendingApplySource`/`pendingApplyForce` races.
- Main UI and floating controls share `BackendApplyQueue`.
- Legacy path remains `TX14 -> TX11`.
- `TX24` is not used for transform.
- `TX25` remains reserved for hard restore only.
- Baked JPEG cache pruning added while the project transitions to realtime GPU rendering.

## Build

Push this repository to GitHub and run the included workflow:

```text
.github/workflows/android.yml
```

The workflow builds:

```text
./gradle assembleDebug
```

using Gradle 8.11.1 from `gradle/actions/setup-gradle`.
