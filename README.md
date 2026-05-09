# IceCam v7.6 dev-flow-perms-ui

v7.6 is still a log-only baseline. It does not inject frames yet.

Changes:
- Dashboard is the single ordered diagnostic flow: 1 -> 6.
- Duplicate action buttons removed from secondary tabs.
- Softer glass UI and slightly more raised buttons.
- Root module permissions widened for dev diagnostics so hooked app processes can read active/config and append hook.log when SELinux allows it.
- Debug bundle now includes icecam/permissions.txt.

Install/test:
1. Install APK.
2. Install root module ZIP.
3. Reboot.
4. Keep LSPosed scope: Camera, Telegram, Chrome, target apps.
5. Open IceCam -> Dashboard -> run steps 1..6.

Expected markers:
[LOAD], [HOOKED], getCameraIdList, getCameraCharacteristics, openCamera.
