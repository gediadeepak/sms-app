# APK, AAB and release pipeline

The workflow **Android build and release** runs on pushes to `main`, pull requests, and manually under GitHub → Actions. It uses Java 25, Android SDK 37, Build Tools 36.0.0, Node 24 and the checked-in Gradle wrapper. External actions are pinned to commit IDs.

## Every push or pull request

1. Check that `app/google-services.json` matches the current `applicationId` and contains the Firebase and Google sign-in configuration. Errors do not print configuration secrets.
2. Run isolated Firebase configuration checks, SQLite/send-queue/paging checks, core and watch-protocol unit tests, and phone debug/release plus watch debug lint.
3. Build phone and watch debug APKs, phone unsigned release APK, and phone unsigned release AAB.
4. Check Firebase resources in the debug and optimized phone APKs.
5. Upload `sandesh-build-<run>` containing named APKs/AAB, the source commit and SHA-256 file checksums. Save test/lint reports separately, including on failure. Download artifacts from the run's Actions page; GitHub packages these downloads as ZIP files.

Debug APKs use the debug App Check provider and need per-installation allowlisting. Unsigned release artifacts are **not ready to install or publish**. This workflow does not deploy Firebase rules, change billing or upload to Google Play.

The package migration to `com.sandeshsms.app` remains local until a genuinely matching Firebase JSON is supplied. The pipeline on `main` builds its committed app identity/configuration. It intentionally rejects a mismatched migration rather than substituting another project's options or shipping disabled account features.

## Configure signed builds once

Repository → Settings → Environments: create **production** and restrict deployment to `main`. Configure required reviewers if desired. Under this environment's secrets add:

| Secret | Value |
| --- | --- |
| `SANDESH_KEYSTORE_BASE64` | Base64-encoded owner release/upload keystore file |
| `SANDESH_KEYSTORE_PASSWORD` | Keystore password |
| `SANDESH_KEY_ALIAS` | Signing key alias |
| `SANDESH_KEY_PASSWORD` | Key password |
| `SANDESH_SIGNING_CERT_SHA256` | Expected public SHA-256 fingerprint of that key (colon-separated or plain hex) |

Base64 is encoding, not encryption. Enter these through GitHub's secret interface; do not commit them or post passwords in chat. Keep the keystore and recovery details backed up securely. You can use PowerShell to copy the encoded file directly to the clipboard:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('C:\secure\sandesh-upload.jks')) | Set-Clipboard
```

Use your actual private file path. Find the signing certificate with `keytool -list -v -keystore <path> -alias <alias>`, entering passwords at the prompt. For this build-time check, the expected fingerprint is the **local key used by this job**, which can differ from Play's final app-signing certificate. Configure Firebase with the certificates actually installed on devices, as described in [the release checklist](RELEASE_CHECKLIST.md).

## Build a signed candidate

1. Push the intended source/configuration to `main`. Bump both application version/code values before a release; do not reuse a published version code.
2. Actions → Android build and release → Run workflow → select **main**.
3. Check **signed_release**. Optionally check **draft_release** to attach the signed outputs to a draft GitHub release. Checking draft alone does not build a signed candidate.
4. The normal test/build job must pass first. The signing job then uses the production environment, builds a signed phone APK/AAB and legacy watch APK, verifies their signing certificates and the AAB signature, checks optimized Firebase resources, and uploads `sandesh-signed-release-<run>`.
5. Inspect the reports, commit and checksums. Test the exact candidate on physical devices, including a second phone. Complete [RELEASE_CHECKLIST.md](RELEASE_CHECKLIST.md).
6. Upload the signed AAB to Play internal testing manually. For public APK sharing compatible with Play signing, obtain the appropriate Play-signed universal APK from Play Console. A GitHub candidate signed with an upload key is not automatically interchangeable with Play's APK.
7. Publish the reviewed GitHub draft and roll out on Play only after the release gates pass. The pipeline creates drafts only; it does not publish releases or store submissions automatically.

The legacy Sony watch module targets API 23 and is for sideloading, not a modern Wear OS Play submission. Keep matching phone/watch package and signing identities when testing its Data Layer connection.

Temporary keystore material is confined to the release runner's temporary directory and removed even on failure. Signing secrets are not supplied to pull-request or ordinary build steps. Production requires the `main` branch and a manual signed-build request; tag pushes alone do not deploy anything. Configure branch protection to require the **build** job before merging.

## Local equivalents and troubleshooting

```powershell
node tools/Validate-FirebaseConfig.cjs
node tools/Test-FirebaseConfig.cjs
node tools/Test-SmsFeatures.cjs
node tools/Test-SmsPaging.cjs
.\gradlew.bat :core:test :watch-protocol:test :app:lintDebug :app:lintRelease :wear:lintDebug :app:assembleDebug :wear:assembleDebug :app:assembleRelease :app:bundleRelease --max-workers=1
```

- **No Android client:** download a JSON registered to the exact application ID. Do not manually rename its client package.
- **Missing OAuth client:** finish Google sign-in/SHA-1 setup and download refreshed configuration.
- **Missing signing secret:** populate all five production environment secrets; an unsigned build is not a fallback for the signed job.
- **Certificate mismatch:** inspect the real keystore certificate and expected secret. Do not disable this check.
- **Device verification failure:** distinguish development-token registration from production Play Integrity; a build cannot prove the live console is configured.
- **SDK/dependency download failure:** inspect Actions logs, repository access and the pinned SDK/tool versions; do not change package identity to resolve a network error.

No production keystore, account administration token, live App Check debug secret or Play publishing service account is stored in the repository. See [GitHub's encrypted-secret guidance](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions) and [Gradle's Actions guide](https://docs.gradle.org/current/userguide/github-actions.html).
