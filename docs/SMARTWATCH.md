# Sony SmartWatch 3 notification testing

The following steps describe phone-side Android Wear 1.5 notification support introduced in 0.6.1. For the separate native launcher inbox added in 0.8.0, see [WATCH_APP.md](WATCH_APP.md). No Bluetooth, microphone or notification-listener permission is added to Sandesh. Pairing and notification access are managed by the Android Wear / Wear OS companion app. Legacy companion availability, pairing, mirroring and voice entry need testing on the actual phone/watch combination.

## Pair and enable

1. Install the new phone APK over the previous debug build without uninstalling.
2. Pair using the compatible Android Wear / Wear OS companion app and the watch's prompts. Allow companion notification access and ensure Sandesh is not blocked. Also enable Sandesh's phone notification permission and Messages channel.
3. In Sandesh open the left drawer → Settings → Smartwatch → Smartwatch settings.
4. Tap **Send watch test alert**. It contains no SMS, sends nothing, and checks mirroring only. Sandesh cannot detect whether the watch received it.
5. Enable **Watch notification actions**. Optionally enable **Show sender and message previews**. Both default off. Preview opt-in affects phone notifications too and may expose sensitive content, including OTPs, on the paired watch. Public lock-screen content is generic, subject to system/companion/watch settings.

## Actions

- Receive a fresh SMS. Generic alerts are retained unless previews are enabled. With previews on, optional Contacts access supplies the sender name; a saved alias or number is the fallback. Previews are bounded to 1,600 characters.
- **Mark read** updates incoming messages through that notification's message. Later unseen messages must remain unread with a silent refreshed notification. Equal timestamps are bounded by provider ID.
- **Draft reply** uses watch input if available, then posts a private **Watch reply ready to review** phone notification. Open it on the phone, review the appended draft, select a SIM and tap Send. No watch action invokes the SMS transport or Gemini.
- The exact thread is used. Existing phone drafts are preserved; empty or over-limit replies and combined drafts exceeding 1,600 characters are rejected without truncation. Alphanumeric senders have no reply action.
- There is one pending review alert per thread. A newer watch reply replaces the earlier pending review alert. Reply text is temporarily held in Android's PendingIntent, outside Sandesh's database and backups. After insertion the composer draft is process-memory-only until sent.
- Spam/blocked/deleted messages and revoked SMS/default-role access reject actions. Changing watch options clears incoming and pending watch-review cards, not SMS. Disabling actions also rejects retained old watch actions. Settings are device-local and excluded from SMS backups.
- Actions use explicit PendingIntents targeting a non-exported receiver. Only reply input is mutable; the original URI binds the target. Watch action intents are one-shot. Nothing sends automatically.

## Device checks still required

Pairing and actions have not been validated on a physical Sony watch. Record phone Android, watch firmware and companion version. Test locked/unlocked phone, DND/notification blocks, disconnect/reconnect, contact revocation, spam/blocking, multipart SMS, a newer SMS while using an old card, equal timestamps, an existing draft and chosen SIM, review while Settings is open, repeated taps, rotation, process death, and blank/1,600/1,601-character replies. Verify no carrier SMS is sent before the normal phone Send action.

References: [Sony Wear 1.5 support](https://www.sony.jp/sp-acc/products/SWR50/), [Sony user guide](https://www-support-downloads.sonymobile.com/swr50/userguide_EN_SWR50_11.pdf), [Android wearable notifications](https://developer.android.com/training/wearables/notifications).
