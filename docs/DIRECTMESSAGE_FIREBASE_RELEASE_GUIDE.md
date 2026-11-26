# DirectMessage: Firebase to Google Play, from scratch

Updated 26 November 2026. This is the current setup guide for **DirectMessage**, Android package **`com.directmessage.app`**. It supersedes project/package examples in the older Sandesh setup notes. The store covered here is **Google Play**, plus direct APK sharing; this repository does not currently provide an iOS/App Store build.

## Current status

- The rename/package changes are prepared locally; their phone build awaits a matching Firebase JSON. They are not yet on `main`.
- The new DirectMessage Firebase project ID/configuration has not been supplied. Do not reuse the old project's registration or manually edit its JSON package.
- The GitHub APK/AAB pipeline is pushed and its test/build path has passed. The manually triggered production job ran and failed because signing secrets were missing. No signed production release was published.
- Production account/data deletion and physical-device release checks remain open. Completing console setup alone does not make the app ready to publish.

## 1. Accounts and services you need

| Service | Required setup |
| --- | --- |
| Firebase / Google Cloud | Owner access to the intended project; separate development and production projects are useful but optional |
| Firebase Authentication | Email/password, Google sign-in, email verification and password reset |
| Cloud Firestore | Default Standard database for encrypted SMS snapshots and source metadata |
| Firebase AI Logic | Gemini Developer API setup and model access |
| Firebase App Check | Development debug tokens; production Play Integrity |
| Google Play Console | Verified developer account, DirectMessage app and Play App Signing |
| GitHub | Repository access and production signing secrets |
| Public website | Privacy policy, support contact and account-deletion request page; Firebase Hosting is optional |

Firebase registration and Google Play app creation are separate steps. A Firebase API key, an App Check token and an Android signing key are also different credentials.

## 2. Create the correct Firebase registration

1. Open the [Firebase console](https://console.firebase.google.com/) → Create project. Name it DirectMessage and record its actual **project ID**. Google Analytics is not required by the current app.
2. Project settings → General → Your apps → Add app → Android.
3. Enter **`com.directmessage.app`** exactly, with nickname DirectMessage. Do not enter the internal Kotlin namespace or an old Sandesh package.
4. Add signing fingerprints as described below. Download the genuine `google-services.json` after Google sign-in is configured.
5. Supply that file for `app/google-services.json`. The phone and watch use the same application ID; the watch does not need its own Firebase SDK registration for its current phone-relay features.

The Android JSON contains client configuration, not an administrator login. It does not carry your Firestore rules, enable AI, register App Check providers or migrate old users. Never supply a service-account private key instead. [Configuration reference](https://firebase.google.com/docs/android/google-services-plugin-and-file)

## 3. Signing certificates: what to put where

| Value | Where to obtain it | Where to use it |
| --- | --- | --- |
| Debug SHA-1 / SHA-256 | `./gradlew.bat :app:signingReport` on the machine that signs the debug APK | Firebase Android app fingerprints for development |
| Owner keystore certificate | `keytool -list -v -keystore <path> -alias <alias>`; enter password privately | CI expected signing fingerprint; Firebase if this certificate actually signs installed APKs |
| Play app-signing SHA-1 / SHA-256 | Play Console → App integrity / App signing | Firebase for Play-delivered installations; SHA-256 for production App Check |
| App Check debug token | The installed debug app's verification screen or Firebase Logcat message | App Check → Apps → `com.directmessage.app` → Manage debug tokens |

The upload key signs the AAB submitted to Play. Play may use a different app-signing key for APKs delivered to users. Register all relevant delivered signing certificates when Play uses multiple keys for different Android versions. Debug APKs from another machine/CI can have another debug certificate.

Do not paste an App Check UUID into SHA fields, invent fingerprints, or share keystore passwords. Back up the signing key securely outside Git. [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)

## 4. Enable registration, login and password reset

1. Firebase → Authentication → Get started → Sign-in method.
2. Enable **Email/Password** using password authentication, and **Google**, selecting a support email.
3. Project settings → Your apps → `com.directmessage.app`: save the signing **SHA-1** fingerprints for supported installed builds. Save the corresponding SHA-256 fingerprints too.
4. Review verification/reset email templates, app branding, sender/support details and Google OAuth consent settings. Resolve any test-user restrictions before public launch.
5. Download the updated JSON. It must select the correct package and include a web OAuth client for the Google sign-in flow; verify an Android OAuth credential exists for the package/SHA-1 pair.
6. Test email registration → verification email → verified login, Google login, logout and password reset on real phones. Cloud backup requires a verified account. A password reset does **not** recover the independent backup password.

These settings belong to the new project; old-project provider configuration does not transfer. [Google sign-in instructions](https://firebase.google.com/docs/auth/android/google-signin)

## 5. Create backup space and private rules

1. Firebase → Firestore Database → Create database. Choose the **default Standard database**, select the intended region and production mode. This app uses Firestore, **not Cloud Storage**, so no storage bucket is required.
2. Review and publish repository [firestore.rules](../firestore.rules). The `sandesh_accounts/{uid}/...` path remains an internal compatible identifier even with DirectMessage branding. Do not rename it independently in the console.
3. The rules allow only the verified authenticated owner UID. Remove public/overlapping wildcard grants. Never leave test-mode public rules for release.
4. To deploy reviewed rules through an authenticated CLI, replace the placeholder with the actual new project ID:

   ```powershell
   firebase deploy --only firestore:rules --project YOUR_DIRECTMESSAGE_PROJECT_ID
   ```

   This replaces the live database rules; review the target first. It has not been executed by writing this guide.
5. Test signed-out, unverified, owner A and different account B access. B must not read or change A's snapshots. Check private rules separately from App Check.
6. Monitor Firestore Usage: storage, document reads/writes/deletes and network traffic. Capacity is shared by users, manual copies and each phone's automatic source. Use measured encrypted snapshot sizes to plan storage rather than assuming unlimited backup space.
7. Free Firestore quota has limits; review the [current quota table](https://firebase.google.com/docs/firestore/quotas) and choose billing if required. Configure alerts and retention/orphan cleanup for growth; alerts alone do not stop spending.

Backups are password-encrypted before upload. File backup is offline and account-free; automatic file backup needs a selected folder and persistent access. Daily/weekly cloud automation is available, but Android may delay jobs. Each installation needs its own local automation setup. Keep an independent encrypted export and its password. Failed cleanup can leave orphan records; no server orphan-expiry service is currently deployed.

## 6. Set up device verification correctly

**Development:** install the matching debug APK → open device-verification settings → reveal/copy its token privately → new project → App Check → Apps → new Android app → Manage debug tokens → register the exact token → retry after propagation. Each installation or cleared-data reinstall may need a new registration. Do not embed a shared debug token in an APK or Git. [Debug provider](https://firebase.google.com/docs/app-check/android/debug-provider)

**Production:** App Check → Apps → `com.directmessage.app` → Play Integrity → supply the actual installed signing certificate's **SHA-256** and complete registration. In Play Console's Play Integrity API area, link the **same Cloud project**. The release build already selects Play Integrity; no debug fallback ships.

Select attestation requirements for the real distribution channels. Follow Google's current table for Play-only, outside-Play-only or both; a sideload is not guaranteed to pass simply because the APK is signed. Test both the Play internal installation and intended shared APK. Review request metrics before enforcing Firestore App Check; keep private rules regardless. AI Logic needs valid App Check too. [Play Integrity setup and distribution guidance](https://firebase.google.com/docs/app-check/android/play-integrity-provider)

Successful account login does not prove device verification is working. Conversely, passing App Check does not prove Firestore rules, account verification or AI quotas are ready.

## 7. Enable AI and plan costs

1. New project → AI Logic → Get started → **Gemini Developer API**. Finish API enablement and review provider terms/data handling.
2. Confirm the configured model, currently `gemini-3.5-flash-lite`, is available in this project at release time. Changing the display name does not enable model access.
3. Keep App Check protection enabled. Review API-key restrictions for the exact package/certificates and required Firebase/AI services; do not paste a standalone Gemini secret into the APK.
4. Review selected model prices, free-tier availability, project quotas and billing requirements. Configure service-side limits and usage monitoring; the app's local request limits are not a financial cap. Budget alerts are notifications, not a guaranteed shutoff.
5. Test summaries and editable reply drafts with synthetic messages first. Verify opt-in, independent toggles, preview/consent, filtering, cancellation and no automatic sending. Leave optional prompt capture off unless reviewed and disclosed.

The new project's settings must be configured independently from the old project. [AI production checklist](https://firebase.google.com/docs/ai-logic/production-checklist) · [Pricing](https://firebase.google.com/docs/ai-logic/pricing)

## 8. Configure GitHub production signing

Repository Settings → Environments → **production** already exists and is restricted to `main`. Add these five secrets privately:

- `SANDESH_KEYSTORE_BASE64`: encoded signing keystore file.
- `SANDESH_KEYSTORE_PASSWORD`: keystore password.
- `SANDESH_KEY_ALIAS`: key alias.
- `SANDESH_KEY_PASSWORD`: key password.
- `SANDESH_SIGNING_CERT_SHA256`: expected certificate fingerprint of the CI signing key.

The existing secret names remain unchanged by the rebrand. Base64 is not encryption. No keystore or passwords are currently configured by this task. Follow [BUILD_PIPELINE.md](BUILD_PIPELINE.md) for private encoding instructions and artifact locations.

After the correct JSON/package changes are committed, run Actions → Android build and release → Run workflow → **main** → `signed_release=true`. Optionally choose `draft_release=true`. A normal push builds test/unsigned artifacts and skips the signing job. Draft creation does not publish to Play.

## 9. Close remaining release gates and submit

1. Implement/test in-app account and associated cloud-data deletion plus a public web request page. Deleting an Auth account alone does not recursively remove Firestore backup chunks. This is still an implementation blocker. [Play deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
2. Publish privacy/support pages and complete Play's Data safety, account-deletion URL, content rating, app access and applicable foreground-service declarations. Describe SMS/contact handling, Google AI processing and encrypted backup accurately.
3. Complete the restricted SMS permissions declaration and default-SMS eligibility review. Verify role-before-permission behaviour. Resolve MMS/RCS limitations before advertising a full replacement messaging app. Recheck [permissions requirements](https://support.google.com/googleplay/android-developer/answer/16558241) at submission.
4. Complete the Play developer account's current identity/testing/production-access requirements and store listing assets. Firebase registration does not satisfy those requirements.
5. Build a signed versioned AAB; inspect the final package, version, signature, Firebase resources, checksums, lint and test reports. Upload to **Play internal testing** first.
6. Add the Play-delivered app-signing fingerprints to Firebase/App Check and test that exact release on a second physical phone: login, reset, AI, notifications, both SIMs, scheduled sends, file/cloud backup and restore. Build success alone is insufficient.
7. For compatible direct APK sharing, obtain the appropriate Play-signed universal APK from Play Console. An upload-key-signed GitHub APK may not update a Play-signed installation. Test the shared APK's attestation too; keep the legacy watch's package/signing identity compatible.
8. Record results, then review and publish the store release and any GitHub draft. The pipeline does not automatically upload to or publish on Play; no Play publishing service account is required for its current manual upload path.

## What the owner needs to provide next

- New DirectMessage **project ID** and refreshed **`google-services.json` for `com.directmessage.app`**, after Google sign-in/fingerprint setup.
- Signing keystore configured privately in the production environment, not posted in chat.
- Confirmation/tests of Auth, Firestore private rules, AI Logic, App Check and billing/quota settings in that new project.
- Public privacy, support and deletion-request URLs before launch.

Old-project accounts/cloud snapshots do not move automatically. Export an encrypted file backup before removing the old app, retain its password, disable old automatic schedules and configure the new app again. See [migration details](DIRECTMESSAGE_MIGRATION.md). No live Firebase deployment, billing change or public store release is performed by adding this guide.
