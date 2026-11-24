# Sandesh release preparation

Version: 0.2.0, application ID `com.gediadeepak.smsapp`, minimum API 24, target API 37.

## Build and signing

Release builds enable R8 code shrinking and resource shrinking, and are not debuggable. APK/AAB builds remain unsigned unless all four variables below are supplied through your local environment or CI secret store:

- `SANDESH_KEYSTORE_FILE`: path to your existing release keystore (outside this repository).
- `SANDESH_KEYSTORE_PASSWORD`.
- `SANDESH_KEY_ALIAS`.
- `SANDESH_KEY_PASSWORD`.

Set these locally without posting passwords in chat or committing them. Android Studio's **Generate Signed App Bundle / APK** wizard is another option. Keep the release key backed up securely; future upgrades require the same signing identity. Debug and release signatures differ, so installing a release APK over the debug build may require uninstalling the debug build first (this removes app metadata).

Run:

```powershell
.\gradlew.bat :core:test :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

Verify the final APK signature with Android SDK `apksigner verify --verbose <apk>`. An unsigned release APK is a build artifact, not an installable published release. The release AAB must also be signed before uploading.

## Publication gates still requiring completion

- Execute and record the physical single-SIM and dual-SIM/eSIM tests in DEVICE_TEST_PLAN.md. Build success and pure Kotlin tests are not carrier tests.
- Resolve MMS/RCS scope before a broad default-messaging release. This candidate explicitly lacks MMS attachment/group support; it must not be marketed as a full replacement for those features.
- Review inbox performance on a large real history and validate permission/role revocation and process-death recovery on supported OEMs.
- Sign with the owner's release key, review the final manifest, and install/test that exact optimized artifact.
- Supply final store assets, support contact and a hosted privacy-policy URL; review the actual Google Play SMS permission declaration, default-handler eligibility and Data safety answers in the owner's Play Console.
- Recheck platform/store requirements at submission. No Play Console publication, signing key generation or store declarations were performed by this coding task.

Automatic app-data backup is disabled and modern extraction rules exclude local data. No export/restore feature or cloud service is included. The system SMS provider is owned by Android and is outside this app's backup controls.
