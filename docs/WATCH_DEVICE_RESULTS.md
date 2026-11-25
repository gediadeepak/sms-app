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

## Watch faces — Sandesh 0.9.0 (code 15)

Verified on the same Sony SmartWatch 3 (API 23, Play services 8.7) and Samsung phone:

- Phone designer opened through Settings → Smartwatch → Smartwatch settings. Final phone screenshot was inspected: square LCD preview, dark page preserved, saved-face controls readable.
- Phone gallery request returned the real watch gallery successfully.
- Phone Apply returned `apply / applied` on the watch and “Face applied on your watch” on the phone. Sandesh was already selected in the system watch-face picker; its wallpaper component was confirmed active. First-time picker confirmation remains a manual platform step.
- Native debug fixtures rendered the display-only LCD and dual-panel retro clock. Active and ambient render assertions passed. All 30 style/color combinations preserved the pixels outside their preview bounds on the physical Sony.
- The earlier launcher crash was a Google launcher OOM while decoding the full-size app logo. The final watch APK uses the same hand/envelope artwork at 128px in drawable-nodpi; the full-size image is debug-only. The watch designer and clock service ran after replacement.
- Final phone/watch debug APKs and debug lint passed; 38 core/protocol tests passed with zero failures. Watch optimized release build and lint passed. Phone optimized release APK, bundle and release lint also passed before the last phone preview-sizing/connection-timeout refinement; those refinements were rechecked in the distributed debug build. Firebase/sign-in resources were verified in debug and optimized phone APKs.

Distributed APKs are development-signed test builds. No SMS was sent during face tests, and exported face fixtures contain generated clock data only. G-TiDE R1 compatibility was not established by these Sony tests.
