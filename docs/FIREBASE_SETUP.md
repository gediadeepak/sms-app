# Sandesh Firebase setup

Project: **sandesh-sms-app**. Android application ID: **com.sandesh.app**.

The supplied client configuration is included in app/google-services.json so a checkout builds against the selected project. Firebase configuration contains public identifiers, not administrator credentials ([Firebase documentation](https://firebase.google.com/docs/android/google-services-plugin-and-file)). Never commit service-account keys, release keystores or administrative tokens. Data protection depends on deployed rules and authentication.

## Authentication

1. Open [Authentication](https://console.firebase.google.com/project/sandesh-sms-app/authentication/providers) and initialize it if necessary.
2. Enable **Email/Password** under Sign-in method (password authentication, not email-link-only). Configure verification and password-reset templates and the support address.
3. Enable **Google**, selecting the project's support email.
4. In Project settings → Your apps → com.sandesh.app, add the debug certificate SHA-1 and SHA-256 fingerprints. Run `./gradlew.bat :app:signingReport` to obtain fingerprints. Register release fingerprints too; for Play distribution use the Play App Signing certificate, as applicable.
5. Download the refreshed google-services.json, replace app/google-services.json, and rebuild. Version 0.3.1 includes the updated file supplied by the owner with a web OAuth client (type 3), enabling the Google button. This file contains no Android certificate client (type 1), so verify that the installed build's signing fingerprint is registered and test sign-in on a real device. Do not invent client IDs.

See [Firebase's Android Google sign-in guide](https://firebase.google.com/docs/auth/android/google-signin). Provider settings cannot be enabled by copying the Android JSON alone.

The app's resource keep file preserves Firebase options and the Google web client ID, which are read by name at runtime, in optimized releases. Check the final APK as well as generated Gradle resources when changing this configuration.

## Private cloud backups

1. Create the default **Cloud Firestore** database in the Firebase console, choosing the appropriate region and production mode. The app uses Firestore, not Cloud Storage.
2. Review current rules first. Merge firestore.rules' sandesh_accounts/{userId}/... namespace into existing rules without changing unrelated applications. Remove any public wildcard overlapping this namespace: Firestore allow rules are additive.
3. Publish the reviewed rules in the console. For a dedicated project with reviewed repository rules, an authenticated CLI can deploy with `firebase deploy --only firestore:rules --project sandesh-sms-app`. This replaces the project's rule set; do not run blindly on a shared project.
4. Confirm anonymous clients, unverified accounts and account B cannot read or write account A's records. Verified account A must be able to upload/download its own encrypted snapshot.
5. Test full backup/restore with a disposable verified account on a physical phone. Monitor Firestore quotas and costs; this task does not change billing plans.

The private rules are prepared in the repository, but have **not** been deployed or tested against the live project by this task. Server rules must enforce account isolation and verification. Broad public rules are unsafe even though payloads are encrypted.

## Behaviour and limits

- SMS works without an account; encrypted file backups work offline through Android's file picker.
- Cloud backup is manual and requires a verified account. A new latest snapshot is published after complete upload. Failed cleanup/interrupted uploads can leave orphaned encrypted records.
- Backups use a separate password of at least 12 characters. Account password reset cannot recover a forgotten backup password.
- Maximum 16 MiB of uncompressed backup data and 100,000 messages; JSON overhead can reach the byte limit first. No MMS/RCS or process-only drafts.
- Restore verifies all data before writes, shows a preview, merges SMS and skips duplicates. A provider failure can leave a partial merge; retry is supported. Historical SIMs become Unknown SIM and the saved sending default resets.
- No automatic scheduling, account deletion or orphan expiry is included. Account deletion and a public deletion-request page are release gates.

## Before distribution

Complete physical-device tests, provider sign-in/reset tests, private-rules access checks and account-data deletion. Update the hosted privacy policy and store declarations for Firebase Authentication and encrypted cloud storage. APK build success does not establish that server settings are live.
