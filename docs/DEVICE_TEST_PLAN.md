# Physical-device acceptance plan

Use test recipients you control. Sending real SMS can incur carrier charges. Never treat an emulator's SMS injection as evidence of carrier delivery.

## Setup and reading

- Install the com.sandesh.app APK (a separate install from the old package). Verify no sample messages appear and system SMS is available after granting access.
- Decline the default role and permissions: the app stays on setup, with no SMS access.
- Grant the default role, then permissions. Verify old inbox/sent messages appear, with correct unread state.
- Revoke SMS or phone-state access while running; the app returns to setup and stops accessing the inbox. Restore access and verify recovery.
- Change the default SMS app away from Sandesh and back. Verify role changes are detected.
- Open a thread, receive a message while foreground/background, and check read/unread handling and privacy-safe notifications. Tap notifications and verify the right thread opens.
- On Android 13+, finish SMS setup and verify the one-time notification permission prompt appears after the splash. Deny it: SMS keeps working and the inbox shows an Enable action. Grant it: the warning disappears after returning to the app.
- Block Sandesh notifications at app level, then block just the Messages channel. Settings must show the correct blocked state and Enable must open the applicable Android notification settings. Already-granted permission must still allow opening channel settings.
- Use Settings → Send test notification; verify a clearly labelled test alert appears in the Android notification panel and tapping it opens Sandesh. Check Android sound/pop-up/DND settings separately from delivery to the panel.
- Receive SMS while a conversation is obscured by the app drawer, settings, account screen, or Android notification panel. It must not be marked read or have its notification cleared until the conversation is visible again. Repeat in the background and with the screen locked.

## SIM choice and sending

- Single SIM: detect its real slot/carrier and send a short SMS to a test number.
- Dual SIM: set Ask every time. Confirm Send stays disabled until a SIM is selected, and the choice is cleared after sending.
- Set each SIM as default in turn; verify the composer and recipient's sender number match that SIM.
- Override the default in the composer for one SMS. Confirm no silent cross-SIM fallback.
- Remove/disable the saved SIM, enable/disable an eSIM, or swap SIMs. Confirm the app requires a new choice and does not use the remaining SIM automatically.
- Send multipart GSM and Unicode/emoji text; verify each segment arrives once and status becomes Sent only after all submission callbacks succeed.
- With delivery reports available, verify Delivered only after reports for all parts. Where reports are unavailable, status must stay Sent.
- Exercise no service, airplane mode, exhausted balance and rejected/premium SMS on a controlled device. Failed or ambiguous sends must never auto-retry. Some segments can have sent even when another fails.
- Kill the app process during a send, relaunch, and check persisted part results; never replace an unknown result with a simulated Delivered state.

## Receiving and metadata

- Receive single/multipart SMS on each line with the app closed. Verify provider persistence, one copy per receipt, correct subscription labels and notification behaviour.
- Temporarily revoke access, receive a controlled SMS, then restore permissions. Check retained pending receipts are drained without duplication.
- Star/archive a conversation, restart the app and verify the flags remain. A new incoming message should unarchive its thread.
- Verify alphanumeric sender IDs display correctly; non-dialable senders must not be sent replies.
- Historical messages from a removed SIM must say Unknown SIM.

## UI, themes and branding

- Inbox rows show a compact SIM icon plus 1/2 immediately before the latest-message date, with no carrier-label row. Unknown subscriptions show a question mark and TalkBack announces Unknown SIM. Verify mixed-SIM threads use the latest message's line, unread counts/stars stay visible, and more rows fit on the phone with the decorative footers removed.
- Blue/white is the default. Switch to green/white and black/white; inspect inbox, bubbles, dialogs, assistant, system bars and composer, then restart to verify persistence.
- Splash and launcher preserve both the winged envelope and supporting hand. Splash tint follows the saved theme.
- Disable system animations: splash should complete immediately. Rotate or recreate the activity: it should not repeat the splash unnecessarily.
- Test large text, TalkBack, keyboard open/close, split screen, portrait/landscape and an empty/large inbox.
- Verify external `sms:`/`smsto:` links only open a draft; no external intent sends automatically.

## Scope checks

- Receive an MMS indication: unsupported state must be visible, not represented as a successfully downloaded attachment.
- Switch back to the previous messaging app to restore full MMS/RCS functionality for future messages.
- Confirm AI suggestions are labelled samples. Network access supports optional accounts/cloud backup; no AI or administrator secrets belong in the APK.
- No physical-device results are claimed by this document. Record model, Android version, carrier, SIM arrangement, artifact hash and outcome for each executed test.

## Accounts and backup

- Open the separate Account and Backup & restore settings entries. Login, registration, password reset, file backup and cloud backup must each show only their own controls. Check all three themes and large fonts.
- Check toolbar Back and Android Back on every page. Passwords must clear when leaving a form or recreating the activity; the email can be retained. Cancel must never submit or restore.
- From Cloud backup, sign in with an already verified account and confirm return to Cloud backup. For a new/unverified account, verify from Account, refresh and navigate back to Cloud backup. Sign-out must not erase SMS or backups.
- Rotate during file selection, backup password entry and restore preview. The right page/dialog must return, passwords must not be restored, and no provider writes may happen until the explicit Restore confirmation.
- Register a disposable email account, verify its email, sign out and log in. Try invalid credentials, duplicate registration, offline mode and verification resend throttling. SMS must work without an account.
- Request and complete a password reset, then sign in with the new password. The independent backup password must remain unchanged.
- With OAuth configuration and signing fingerprints registered, exercise Google sign-in, cancellation, sign-out, restart and account switching on a Google Play services device. Test the signed release too.
- Export Unicode/multipart SMS, stars, archives, aliases and all themes. Check the exported file contains no readable SMS. Save through local/cloud document providers and restore on another phone with the backup password.
- Wrong password, truncated/corrupted files and oversized input must fail before changing SMS. Cancel file selection, password entry and restore preview; inbox stays unchanged.
- Restore into empty and overlapping inboxes, then repeat: messages must not duplicate or disappear. Restored outbox/queued/failed records must never send. Historical SIMs show Unknown SIM; the sending default requires choosing again.
- Revoke the default role during restore and test process death. Partial merges must be reported; retry must skip restored rows. Never erase current messages.
- Back up with verified account A, then restore on another phone. Anonymous clients, unverified A and verified B must be denied access to A's Firestore paths by server rules, even when bypassing the Android UI.
- Interrupt upload and confirm the previous published backup stays usable. Interrupt download and verify no provider writes. Test expired sign-in, missing rules/database and quota errors without false success notices.
- Verify sign-out/uninstall do not imply remote deletion. Complete account/data deletion before store distribution.

## Spam and headset readout

- Upgrade an existing database from version 2 to 3; stars, archives, aliases, send receipts and retained incoming SMS must survive. New spam flags start false and the block list starts empty.
- Move a thread to Spam. Check it disappears from Inbox, Unread, Starred and Archived while remaining readable/recoverable in Spam. New SMS in that thread is stored without notifications or speech.
- Block numeric and alphanumeric senders, restart the app, and receive fresh SMS. Messages remain in Spam. Unblock via both the conversation and Settings → Blocked senders; verify future SMS returns to normal notifications. Calls and other apps must be unaffected. Nothing is deleted or sent by block/unblock.
- Restore an older backup without spam fields and a new backup with spam/blocks. Existing SMS remains; block lists merge, no restored message is sent/announced, and device-specific headset settings are not imported.
- Test headset readout with wired/USB/Bluetooth outputs on Android 9+; verify it is unavailable on API 24–27. Select a private output, enable announcements and receive fresh SMS: only the sender is spoken. Body speech requires an explicit Read action and an unlocked phone.
- Use Test headset and Stop before testing private content. Verify an installed offline voice is required. Test missing voice, focus denial, calls, DND, screen lock, background restrictions and force-stop recovery without crashes or repeated old announcements.
- During speech disconnect the selected output, connect a speaker/car, switch routes, interrupt audio focus and press Stop. Audio must stop without exposing message content through another output. On Android 16+ verify all reported routes match the chosen device. Test process death/temporary file cleanup and unidentified output reselection.
- Repeat the release-gating checklist in SPAM_AND_HEADSET.md before publication; no physical audio-routing results are claimed by build/unit tests.

## Settings navigation (0.4.1)

- Open Settings from the left drawer on a phone and the persistent sidebar on a wide display. Confirm a full page replaces the inbox, and Back returns to the previous conversation/folder.
- Expand each section; only one is expanded at a time. Check all three themes at large font size, with keyboard and after rotation.
- Change theme, default SIM and reply ideas; close/reopen and restart to confirm persistence. Save SIM labels; whitespace-only labels must not save.
- Open Account, login, register, reset, Backup, File and Cloud pages; toolbar/system Back returns through the expected pages to Settings. Revisit after rotation.
- Open backup password and restore preview pages; Back cancels without changing SMS. Do not retain passwords through rotation/process recreation. Verify normal encrypted export/restore still works.
- Open Blocked senders and Headset readout. Back returns to the same expanded Settings section and scroll position. Unblock confirmation is also a page; Cancel returns without changing the block list.
- While Settings/account/backup is visible, receive an SMS in the previously selected conversation: it must stay unread and notify normally.
- In Headset readout, open Speech voice settings. Verify Android TTS settings opens (or Android Settings as a fallback), then return to the same page and test an installed offline voice.
