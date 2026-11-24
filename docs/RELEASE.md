# Sandesh release preparation

Version: 0.3.3 (code 6), application ID `com.sandesh.app`, minimum API 24, target API 37. The inbox uses compact two-line rows with numbered SIM icons before dates, and removes decorative phone footers to show more conversations. Account and Backup & restore have separate settings entries and focused pages. This installs separately from the earlier `com.gediadeepak.smsapp` candidate; private settings do not migrate automatically. System SMS remains available after permission setup. The supplied web OAuth client is included for Google sign-in; live sign-in still requires the correct Android signing fingerprint registered in Firebase.

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

Check Firebase resources in both debug and optimized APKs after configuration changes:

```powershell
.\tools\Test-FirebaseApk.ps1 -ApkPath app/build/outputs/apk/debug/app-debug.apk -Aapt2Path '<SDK>/build-tools/36.0.0/aapt2.exe'
.\tools\Test-FirebaseApk.ps1 -ApkPath app/build/outputs/apk/release/app-release-unsigned.apk -Aapt2Path '<SDK>/build-tools/36.0.0/aapt2.exe'
```

This checks that required configuration names survive resource shrinking. Actual Google account sign-in still needs physical-device validation.

## Publication gates still requiring completion

- Execute and record the physical single-SIM and dual-SIM/eSIM tests in DEVICE_TEST_PLAN.md. Build success and pure Kotlin tests are not carrier tests.
- Complete [Firebase setup](FIREBASE_SETUP.md): enable providers, register signing fingerprints, create Firestore and publish reviewed private rules. The supplied JSON now includes the web OAuth client. Live account flows and rules have not been verified by the build.
- Test encrypted file and cloud restore on actual devices, including wrong passwords, damaged files, duplicate merges, interrupted operations and account isolation.
- Implement account/cloud-data deletion and a public deletion-request route before publishing account creation; this candidate has no deletion flow.
- Resolve MMS/RCS scope before a broad default-messaging release. This candidate explicitly lacks MMS attachment/group support; it must not be marketed as a full replacement for those features.
- Review inbox performance on a large real history and validate permission/role revocation and process-death recovery on supported OEMs.
- Sign with the owner's release key, review the final manifest, and install/test that exact optimized artifact.
- Supply final store assets, support contact and a hosted privacy-policy URL; review the actual Google Play SMS permission declaration, default-handler eligibility and Data safety answers in the owner's Play Console.
- Recheck platform/store requirements at submission. No Play Console publication, signing key generation or store declarations were performed by this coding task.

Automatic app-data backup is disabled and modern extraction rules exclude local data. Manual encrypted file backup and optional Firebase account/cloud backup are included. The system SMS provider is owned by Android and is outside this app's automatic-backup controls. Backup passwords are independent of account passwords and cannot be recovered by password reset.
