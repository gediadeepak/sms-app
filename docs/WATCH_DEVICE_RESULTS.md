# Sony watch device results — 25 November 2026

Build: Sandesh 0.8.0, code 14. Watch: Sony SmartWatch 3 / tetra, 320×320, Android 6.0.1 API 23, build M1D64T, installed Google Play services 8.7.01. Paired phone: Samsung SM-G780F, updated from Sandesh 0.7.0 using a data-preserving APK replacement.

Passed on the actual connected devices:

- Debug phone/watch signing certificates match. Watch APK installed successfully and exposes the Sandesh launcher activity. Native activity launched successfully.
- Initial request with the phone's watch-inbox opt-in off returned `inbox / disabled` on both devices and displayed setup instructions. No SMS preview query was needed for this handshake.
- The owner enabled the phone setting. A new native watch request returned `inbox / ok` on both devices.
- Selecting a visible conversation card returned `thread / ok` and rendered the detail view.
- Tapping Open on phone returned `open / opened`, meaning the phone accepted the local review notification, with a matching instruction shown on the watch. Tapping that notification on the phone was not automated in this test.
- Leaving the foreground produced the preview-cleared lifecycle status. UI dumps used to locate controls were held privately for bounds inspection and removed from the device. Sender names and bodies were not printed.

No SMS Send control or carrier transport was invoked. Diagnostic output included command/status only. These results do not establish all theme/layout combinations, the one-minute active-view expiry, disconnected revocation, permission loss, background reconnect, unread semantics or every phone-notification navigation case; those remain in WATCH_APP.md's device checklist.

Local validation: 33 core tests plus 2 protocol tests passed; phone/watch debug and optimized release APKs, phone release bundle and both variants' lint tasks completed successfully. Release signing/publication requirements in RELEASE.md still apply.
