# DirectMessage registration and migration

The selected public name is **DirectMessage**. The selected Android application ID is **`com.directmessage.app`** for both the phone and legacy Sony watch. Internal source namespaces and portable backup formats retain their existing identifiers for compatibility; they are not the Firebase registration package.

Start with the complete [Firebase-to-Play guide](DIRECTMESSAGE_FIREBASE_RELEASE_GUIDE.md) for accounts, backup space, AI, tokens, signing and publication from scratch.

## Owner Firebase setup

1. Create/select the intended DirectMessage Firebase project. Its project ID has not yet been supplied to this checkout.
2. Project settings → General → Add app → Android: register **`com.directmessage.app`** exactly, with nickname DirectMessage.
3. Enable Email/Password and Google under Authentication. Add the actual debug/release signing SHA-1 fingerprints to this Android registration and download its genuine `google-services.json`. Do not change an old JSON's package text by hand.
4. Provide the downloaded file so `app/google-services.json` can be replaced and the new project ID can be recorded in the setup documents. The currently checked-in configuration belongs to the previous Sandesh registration and cannot build the new application ID.
5. Configure AI Logic → Gemini Developer API, create the default Standard Cloud Firestore database for encrypted SMS backup, and deploy the reviewed private rules. The app uses Firestore, not a Cloud Storage bucket.
6. Register new-installation App Check debug tokens for development. For production, configure Play Integrity using the installed signing certificate's SHA-256, with distribution settings appropriate to both Play and shared APKs.
7. Complete [the release checklist](RELEASE_CHECKLIST.md) and [production signing setup](BUILD_PIPELINE.md). Project IDs and package names mentioned in older setup notes refer to the previous registration until the replacement configuration is supplied.

## Existing installations

The new application ID installs separately from `com.sandesh.app` and the previously proposed `com.sandeshsms.app`. It does not inherit private preferences, queued sends, local Android Keystore credentials or app metadata. Android's system SMS remains available after granting the new default-SMS app access.

Export an encrypted file backup before removing an old app. Keep its independent backup password and disable old automatic backup schedules before switching. A new Firebase project has separate Auth users and Firestore records; existing accounts/cloud snapshots do not move automatically. Restore the portable file and configure accounts, permissions, backup schedules and device verification again. Update both phone and watch to the matching package/signing identity.

The visible name has been changed in launcher labels, onboarding, account pages, notifications, headset prompts and watch UI. Existing internal database names, backup format identifiers and Wear message paths remain compatible. Existing user-named watch designs are not overwritten by the branding change.
