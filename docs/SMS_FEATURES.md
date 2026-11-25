# SMS features in Sandesh 0.10

## Sending later and Undo

Write a message, choose a SIM and tap the clock beside Send. Enter a future local date/time in `yyyy-MM-dd HH:mm` format. Scheduled work runs at or after that time; Android power management, force-stop, reboot/unlock and background restrictions can delay it. The schedule keeps an absolute instant and the selected subscription, not a SIM slot that might later hold another card.

Normal Send holds messages for at least ten seconds by default. Use Undo beneath the composer or **Scheduled & Undo** in the drawer. Settings → Sending & organization can turn the hold off. The drawer page remains accessible from SMS setup when permissions/default-app access are missing.

Cancellation and worker claim compete through an atomic SQLite update. Once claimed, cancellation cannot recall a carrier handoff. A retried/interrupted claimed job becomes Review instead of sending again. Removed SIMs, missing permissions, blocked/spam senders and messages over 24 hours late require review. Android delivery receipts remain in the normal conversation. There is no automatic retry of a failed SMS and no MMS scheduling.

Upcoming jobs survive process recreation through WorkManager and the local queue. Queue bodies stay in private app storage. Queued jobs, automatic-backup credentials and device verification tokens are deliberately excluded from SMS backups so restoring on another phone cannot duplicate scheduled sends.

## Pins and business groups

Use the pin icon in a conversation to pin/unpin it. Up to ten pins are hydrated independently of the first fifty recent conversations. Search, SIM filters, spam exclusions and the normal paging cursor stay intact.

Use the business icon to assign a group name, or enable grouping in Settings → Sending & organization. A small local sender-ID dictionary suggests known businesses; numeric phone numbers and message bodies are not used to guess a brand. Original threads, sender IDs and SIMs stay separate. Group headings expand into their conversations and count loaded threads only. Use **Load 50 more** to extend grouped results; grouped mode avoids automatic page-filling when collapsed groups fit on screen. Search also matches custom group labels and recognized business names. Pins remain individually visible above groups.

Pins, custom labels and organization preferences are included in password-encrypted message backups, with optional fields for compatibility with older backups.

## Automatic encrypted backups

Backup & restore → Automatic backups provides opt-in daily/weekly folder or cloud backups. Select a persistent-access folder, or sign in with a verified account for cloud mode. Confirm a backup password of at least twelve characters. Only after enabling this feature is that password stored, encrypted by an Android Keystore key local to this installation. The backup files themselves still use portable password encryption; retain the password to restore on another phone. Account password resets cannot recover it.

Run backup now tests the configured destination. The page shows the latest automatic success and error, and Backup home shows completed file/cloud backup timestamps. Folder mode tracks seven completed copies for the current configuration, writing the new copy first and attempting to remove older tracked copies. Automatic cloud backups use a separate source for each installation, preserving other phones' copies and the shared manual copy. Cloud restore lists the available sources by phone label and date. Existing manual backups remain compatible. Complete-snapshot publication and cleanup apply within each source. Android may delay periodic work. Network is required for cloud work; low-storage conditions defer either mode.

Signing out disables cloud automation and forgets its stored password. Disable & forget stops future automatic jobs and removes the stored credential. Already completed backups remain. Set up automation again on a new phone; it is not restored with message history.

## Cloud backup on another test phone

File backup does not require Firebase. Cloud backup requires both a verified account and App Check device verification. Copying a development APK or signing in does not register another installation's debug token.

1. On that phone, open Backup & restore → Cloud backup & restore → Device verification help.
2. Tap Check this device, then Show this development device's token and Copy token. Keep it private.
3. In the **sandesh-sms-app** Firebase project, open Security → App Check → Apps → the Sandesh Android app → Manage debug tokens. Register that phone's token.
4. Retry device verification and cloud backup. If verification succeeds but Firestore denies access, refresh the account and check its verified-email claim and the deployed Firestore rules.

The helper reads the pinned debug SDK's own existing private token; it does not replace tokens, put a shared secret in the APK or bypass verification. Production builds use Play Integrity and hide the debug-token control. Production distribution still requires the correct release certificate and Firebase attestation configuration.

Official references: [App Check debug registration](https://firebase.google.com/docs/app-check/android/debug-provider), [production Play Integrity](https://firebase.google.com/docs/app-check/android/play-integrity-provider), [WorkManager](https://developer.android.com/jetpack/androidx/releases/work).

## Validation and remaining device checks

Core/protocol tests cover timing bounds, stale work, known/unknown sender identities, pin ordering, separate cloud-source identities and the existing SMS/backup behavior. `node tools/Test-SmsFeatures.cjs` executes source-derived SQLite schema/claim/cancel predicates for migrations and both cancellation orderings. `node tools/Test-SmsPaging.cjs` checks provider paging/search behavior. A debug-only FeatureCheckActivity also provides isolated Android DB and Keystore checks without reading SMS or invoking SmsManager.

The owner chose to test the APK later; phones were disconnected during this development pass. Real scheduled delivery, background/reboot behavior, SAF provider persistence, Keystore round-trip on the target phones and cross-phone cloud restore still need device testing. Start with a message to a number you control and confirm the selected SIM and delivery receipt. Register the second phone before testing cloud backup. Automated checks do not establish carrier delivery or completion of Firebase console configuration.

Validation for the distributed 0.10.0 test build (25 November 2026): 44 core/protocol tests passed; 10 source-SQL migration/queue checks passed; the existing 153-conversation/127-history-row paging SQL checks passed. Debug and optimized release APKs, release App Bundle and debug/release lint completed successfully (zero lint errors, existing/library/style warnings remain). Persisted worker class names survived optimization; production manifest excluded both debug diagnostics. Firebase/sign-in resources were verified in both APK variants. The final development APK signature matched the existing Sony watch build. Physical-phone, carrier, SAF/Keystore and live cross-phone Firebase testing remain pending by the owner's choice.
