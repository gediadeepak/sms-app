# Physical-device acceptance plan

Use test recipients you control. Sending real SMS can incur carrier charges. Never treat an emulator's SMS injection as evidence of carrier delivery.

## Setup and reading

- Install the com.sandesh.app APK (a separate install from the old package). Verify no sample messages appear and system SMS is available after granting access.
- Decline the default role and permissions: the app stays on setup, with no SMS access.
- Grant the default role, then permissions. Verify old inbox/sent messages appear, with correct unread state.
- Revoke SMS or phone-state access while running; the app returns to setup and stops accessing the inbox. Restore access and verify recovery.
- Change the default SMS app away from Sandesh and back. Verify role changes are detected.
- Open a thread, receive a message while foreground/background, and check read/unread handling and privacy-safe notifications. Tap notifications and verify the right thread opens.

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
