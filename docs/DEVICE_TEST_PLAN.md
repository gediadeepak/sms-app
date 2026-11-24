# Physical-device acceptance plan

Use test recipients you control. Sending real SMS can incur carrier charges. Never treat an emulator's SMS injection as evidence of carrier delivery.

## Setup and reading

- Install the debug APK over the earlier prototype. Verify no sample messages appear.
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

- Blue/white is the default. Switch to green/white and black/white; inspect inbox, bubbles, dialogs, assistant, system bars and composer, then restart to verify persistence.
- Splash and launcher preserve both the winged envelope and supporting hand. Splash tint follows the saved theme.
- Disable system animations: splash should complete immediately. Rotate or recreate the activity: it should not repeat the splash unnecessarily.
- Test large text, TalkBack, keyboard open/close, split screen, portrait/landscape and an empty/large inbox.
- Verify external `sms:`/`smsto:` links only open a draft; no external intent sends automatically.

## Scope checks

- Receive an MMS indication: unsupported state must be visible, not represented as a successfully downloaded attachment.
- Switch back to the previous messaging app to restore full MMS/RCS functionality for future messages.
- Confirm AI suggestions are labelled samples and no network permissions or API secrets are present.
- No physical-device results are claimed by this document. Record model, Android version, carrier, SIM arrangement, artifact hash and outcome for each executed test.
