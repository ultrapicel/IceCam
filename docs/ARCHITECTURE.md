# IceCam architecture

## Layers

1. IceCam App
   - UI
   - diagnostics
   - media source control
   - camera profile inspection

2. Root Module
   - service scripts
   - native libraries
   - logs
   - control binary

3. Hook Layer
   - LSPosed/Zygisk Java hooks
   - native hooks
   - Camera metadata spoof layer

4. Stream Engine
   - MediaCodec pipeline
   - OpenGL transform stage
   - pan/zoom/rotate/mirror
   - front/back profile routing

## Development policy

- dev builds log aggressively
- release builds log only errors
- no direct patching of foreign APK DEX files
- GitHub Actions is the canonical build environment
