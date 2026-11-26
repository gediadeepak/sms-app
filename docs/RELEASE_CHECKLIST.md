# Sandesh: Firebase and public release checklist

Prepared 26 November 2026. Complete the steps in order. Check boxes mean verified completion, not just opening a console page. See [release preparation](RELEASE.md) for build commands and the remaining product gates.

## 1. Confirm the project and app identity

| Item | Intended value / current status |
| --- | --- |
| Product name | Sandesh |
| Phone and watch application ID | `com.sandeshsms.app` (local migration prepared) |
| New Firebase project ID | `sandeshsms-3bbcb` |
| Previous project | `sandesh-sms-app`; retain until migration is verified |
| Supplied `google-services (5).json` | New project, but registered to **`com.sandesh.app`**; cannot build the requested new package |
| Google sign-in in supplied new JSON | No OAuth clients; setup incomplete |
| Build / publication status | New-package APKs blocked on matching configuration; not released |

- [ ] Open the [new Firebase project](https://console.firebase.google.com/project/sandeshsms-3bbcb/overview).
- [ ] Project settings → General → Your apps → Add app → Android: register **`com.sandeshsms.app`** exactly. Do not manually edit the old JSON's package string.
- [ ] Complete fingerprints and Google provider setup below, then download the matching `google-services.json` and replace `app/google-services.json`.
- [ ] Confirm its project ID and matching Android client before building. A JSON can legitimately contain multiple Android clients.

Changing both project and package creates a fresh app/account environment. Old Firebase users and cloud backups do not automatically move to the new project. Export an encrypted file backup while the old app/account works. Keep its password. Disable old automatic backup schedules before switching; choose the new app as default SMS, grant access, restore the portable file, and configure schedules again. Android's system SMS history stays on the phone. Private settings, pending send jobs, credentials and device verification do not migrate automatically. Update the watch package too.

## 2. Decide production signing before distributing

- [ ] Create the Sandesh app in **Google Play Console**, separately from Firebase registration.
- [ ] Enrol in Play App Signing and keep the upload keystore/passwords securely backed up outside Git. The upload key and Play app-signing key have different purposes.
- [ ] Obtain the public **SHA-1 and SHA-256 of the app-signing certificate** from Play Console's App integrity / App signing area. Register all applicable certificates if Play uses different signing keys for different Android versions.
- [ ] For direct APK sharing with compatible Play updates, use the **signed universal APK downloaded from the same Play release** where available. A locally upload-key-signed APK is not necessarily signed like Play's delivered app.
- [ ] Keep phone and legacy watch packages signed with compatible matching identities for Wear Data Layer. Do not assume a debug-signed watch can communicate with a production-signed phone; plan and test their signing/distribution together.

For local debug fingerprints, run `./gradlew.bat :app:signingReport`. For an owner-managed release keystore, use `keytool -list -v -keystore <path> -alias <alias>` and enter the password at its prompt. Never paste passwords/private keys into chat. A debug certificate is for development, not the public production identity. [Play signing reference](https://support.google.com/googleplay/android-developer/answer/9842756)

## 3. Configure accounts and Google sign-in

- [ ] New project → Authentication → Get started → Sign-in method: enable **Email/Password** and **Google**; save the Google support email.
- [ ] Project settings → General → Your apps → **`com.sandeshsms.app`**: add the **SHA-1** for every certificate actually used by supported builds. Add SHA-256 as appropriate for the same signing identities.
- [ ] Download the updated JSON after saving. Check that Google sign-in has a web OAuth client (`client_type: 3`) and that an Android OAuth credential exists for the exact package/certificate pair. The last supplied file has neither client for its new project.
- [ ] Configure email verification/password reset templates, app name, sender/support details and the OAuth consent/branding configuration. Resolve any consent-screen testing restrictions before public use.
- [ ] Test registration, verification email, verified sign-in, sign-out, password reset and Google sign-in on real devices. Cloud backup requires a verified account; email password reset does not recover the separate backup password.

Provider switches are project-wide; registering a package does not enable them in a newly created project. [Firebase Google sign-in instructions](https://firebase.google.com/docs/auth/android/google-signin)

## 4. Configure backup space and private access

Sandesh currently stores **encrypted backup chunks in Cloud Firestore**, not Firebase Cloud Storage. No bucket is needed for this implementation. It uses `sandesh_accounts/{uid}/state/{source}` and `sandesh_accounts/{uid}/backups/{backupId}/chunks/{index}`. Manual backups and automatic backups from different installations have separate source pointers.

- [ ] New project → Firestore Database → Create database: use the **default database, Standard edition**, select the required region and production mode. Review the location before creating it.
- [ ] Review repository `firestore.rules`. Publish its private namespace rules in the new database. They require a signed-in, verified account whose UID matches the path. Do not use public test-mode rules or an overlapping wildcard that grants access.
- [ ] Optional CLI deployment, only after confirming this is the intended dedicated project: `firebase deploy --only firestore:rules --project sandeshsms-3bbcb`. This changes live rules; it is not performed by adding this document.
- [ ] Test access with signed-out, unverified, account A and account B clients. Only verified A should read/write A's data. Test rules and App Check independently.
- [ ] Open Firestore Usage and billing/quota pages. Free quota currently includes **1 GiB stored data**, 50,000 document reads/day, 20,000 writes/day and 20,000 deletes/day for an eligible database; verify current limits before launch. This is shared project capacity, not a per-user allowance. Beyond free capacity, plan billing and usage controls. [Firestore limits](https://firebase.google.com/docs/firestore/quotas)
- [ ] Estimate storage using measured encrypted backup sizes × users × retained device sources, plus metadata/index overhead. A hypothetical 5 MiB snapshot × 200 users is about 1 GiB before overhead, extra devices or manual copies.
- [ ] Test automatic and manual backup, second-phone source selection, interrupted upload, password failure, damaged files and duplicate-safe restore. Keep a working encrypted file export independent of Firebase.
- [ ] Establish storage retention and orphan cleanup. Automatic folder backup attempts to retain seven tracked files; cloud snapshots can leave orphan chunks after interruption/cleanup failures. No server orphan-expiry service is currently deployed.

Current app limits include 16 MiB uncompressed backup content and 100,000 messages. Encryption is password-based AES-GCM with PBKDF2; the backup password is not uploaded. Automatic-backup credentials are wrapped with the local Android Keystore and must be configured on each phone. File backups need user-selected folder access, not a Firebase account. Daily/weekly background scheduling is available; Android may delay execution.

## 5. Configure device verification (App Check)

App Check is different from account login. A user can sign in successfully while cloud backup or AI fails device verification. Configure the **new project's new Android app**, not the previous registration.

| Credential | Purpose | Where it belongs |
| --- | --- | --- |
| Certificate SHA-1 | Google Android OAuth identity | Firebase app fingerprints / Android OAuth credential |
| Certificate SHA-256 | Production signing identity for Play Integrity | Firebase app fingerprints and App Check Play Integrity setup |
| App Check debug token | Allow a development installation | App Check → Apps → new app → Manage debug tokens |
| Web OAuth client ID | Google sign-in audience | Genuine updated Firebase JSON / generated resources |
| User ID/access tokens | Logged-in account access | Managed at runtime by Firebase SDK; never hardcode |
| Backup password | Decrypt portable SMS backup | User-held; local protected storage only for opt-in automation |

- [ ] For development: install the new debug APK and use Settings' device-verification check. Reveal/copy its token privately, or inspect the Firebase App Check Logcat message. Register that exact token in the **new project's** Manage debug tokens. Retry after propagation. Do this for each installation as needed.
- [ ] Do not generate an unrelated UUID and expect it to match the device. Clearing data/reinstallation can produce a different token. Never commit or distribute a shared debug secret.
- [ ] For production: App Check → Apps → `com.sandeshsms.app` → Play Integrity. Add the correct production signing SHA-256 and complete setup. The release source set already selects Play Integrity; there is no debug fallback.
- [ ] In Play Console, link Play Integrity to the **same new Cloud/Firebase project**.
- [ ] Choose attestation requirements for actual distribution. Google's current guidance for **Play plus outside-Play distribution** requires `PLAY_RECOGNIZED` but not `LICENSED`. Follow the provider's current distribution table; an unpublished or differently signed sideload may still fail. Test the exact shared release APK, not just a Play installation.
- [ ] Review verified/unverified request metrics. Enforce Firestore App Check after the intended release paths are verified; keep AI Logic App Check protection enabled. Security rules remain necessary even with App Check.

[Development token instructions](https://firebase.google.com/docs/app-check/android/debug-provider) · [Production provider and distribution settings](https://firebase.google.com/docs/app-check/android/play-integrity-provider)

## 6. Enable real AI and control usage

- [ ] New project → AI Logic → Get started → **Gemini Developer API**. Complete API enablement and review the provider terms. Enabling it in the old project does not enable this new project.
- [ ] Confirm the code's model, currently **`gemini-3.5-flash-lite`**, is available to this project and supported at launch. Test both summaries and reply drafts with synthetic messages.
- [ ] Keep AI optional and the separate summary/reply controls. Confirm preview/consent, sensitive-message filtering, cancellation, stale-result rejection and editable drafts. AI must never send SMS automatically.
- [ ] Review the provider's data-use/retention terms and selected billing tier before sharing real SMS. Disclose Google processing accurately. Leave optional prompt/response monitoring capture off unless explicitly reviewed and disclosed.
- [ ] Set provider quotas/rate limits appropriate to expected users, monitor usage and configure billing alerts if billing is enabled. App-side limits are not a server spending cap. Budgets notify; they do not automatically stop spending.
- [ ] Confirm API-key restrictions support Firebase AI Logic and Auth for the new package/certificates. Do not paste a raw Gemini secret into app code or copy a different project's keys.

Firebase AI Logic pricing follows the chosen provider/model and project billing setup. Check current prices and free-tier availability rather than assuming earlier estimates still apply. [Pricing](https://firebase.google.com/docs/ai-logic/pricing) · [Production checklist](https://firebase.google.com/docs/ai-logic/production-checklist)

## 7. Finish product and store release gates

- [ ] Implement and test **in-app account/associated cloud-data deletion**, plus a public web deletion-request route. Deleting an Auth user alone does not delete Firestore subcollections. This is still a code/service release blocker, not a Firebase console toggle. [Google Play deletion requirements](https://support.google.com/googleplay/android-developer/answer/13327111)
- [ ] Publish a privacy policy and support contact. Complete Data safety, content rating, app access/reviewer instructions, ads declarations and applicable foreground-service declarations accurately.
- [ ] Complete the SMS permissions declaration and demonstrate default-SMS core functionality. Request the default role before restricted permissions; stop restricted access if the role is lost. Google Play review still determines eligibility. Resolve the current MMS/RCS scope before marketing a full messaging replacement. [Permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241)
- [ ] Complete Play Console's current account verification, testing and production-access requirements for this developer account. Recheck target API and submission requirements at upload.
- [ ] Build/lint/test the final configuration and sign the release AAB. Check final APK package/version/signature and Firebase resources, then install the exact optimized release on a second physical phone.
- [ ] Test notifications, SIM selection, real carrier send/delivery, copy/share/contacts, paging, scheduled send/Undo, reboot recovery, backup/restore, login, AI and watch sync. Do not use live carrier sends in automated fixture tests.
- [ ] Upload to Play internal testing first. Verify App Check, Auth, AI and backup on both a Play install and the intended direct APK. Record artifact checksums and test results before public rollout.

Do not treat a successful build, matching JSON, Firebase app registration or working debug token as proof that the app is production ready. No live rules deployment, billing change, Play submission or new-project end-to-end verification was performed by writing this checklist.
