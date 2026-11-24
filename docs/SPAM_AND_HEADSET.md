# Spam and headset readout

## Spam and sender blocking

Open a conversation → More (three dots) → **Move to Spam** or **Block sender**. Both require confirmation. Open **Spam** from the navigation menu to inspect and restore messages. **Not spam / Unblock & move to inbox** restores a conversation. **Settings → Blocked senders** lets you unblock even when no current SMS remains from that sender.

This is manual, app-local filtering. No AI classifier or automatic deletion is enabled. SMS remains in Android's provider. Spam/blocked conversations stay out of ordinary folders and do not produce SMS notifications or headset speech. Replying requires restoring/unblocking first. Calls and other apps are not blocked. Number formatting is normalized, but local/international country-code variants and different business sender IDs are not guessed or merged.

Database version 3 adds a spam flag and a separate sender-key table without rebuilding old data. Unblocking restores loaded conversations and new SMS notifications. Encrypted backups carry spam flags and blocked senders; old backups without those optional fields remain accepted. Restore merges saved blocks with existing blocks and does not enable headset announcements on another phone.

## Headset setup

1. Use Android 9 or newer and enable Sandesh message notifications.
2. Connect your headphones. Open **Settings → Headset readout** and explicitly select the intended private output. Android's broad Bluetooth audio type can also include speakers/cars; select the correct device yourself.
3. Use **Test headset**, then **Stop readout**. Install an offline voice in Android text-to-speech settings if the test reports that no voice is available.
4. Enable **Announce SMS senders** if wanted. It is off by default and speaks only the sender of newly received, non-spam SMS. Pending-history recovery and restore never announce old messages.
5. To hear a body, unlock the phone and choose **Read message** on its notification or tap the speaker beside that incoming message in the thread. Spam/blocked messages are excluded. Very long messages are capped to the speech engine's input limit with an audible notice to read the remainder in the app.

Readout synthesizes to an app-private temporary file with an installed offline voice, then uses a short-lived mediaPlayback foreground service. The player begins muted and is unmuted only after checking that its actual route matches the chosen output. Disconnect/noisy broadcasts, route changes and audio-focus loss stop it; private SMS is not resumed automatically. The ongoing notification includes Stop. A process restart never replays old speech.

Selected audio devices and the announcement preference are local to the phone and excluded from backup. Outputs without stable device addresses are selected for the current process session only and may require reselection after the app process restarts. Android/OEM restrictions or audio-focus denial can skip automatic announcements; the ordinary SMS notification and manual Read action remain available. Android 7–8 retain SMS/spam features but the readout service is disabled through a version-qualified resource and guarded startup.

## Mandatory device acceptance before release

Test each supported wired, USB and Bluetooth route on physical devices, including Samsung and the exact optimized/signed APK. Verify initial playback, disconnect/reconnect, Bluetooth speaker/car switching, multiple outputs, calls, music, focus loss, lock screen, Stop, app/task/process death and notification permission/channel revocation. No private text may become audible on a different output. Android 16+ checks all reported output routes; earlier versions expose only the older routing API, so OEM behaviour must be verified. If a route/OEM cannot meet that requirement, do not enable the feature for it.

Also validate database migration and restore/block-list merging against a disposable SMS history. Core tests cover folder isolation, sender matching and fail-closed route-guard transitions; they do not establish Android audio routing, carrier behaviour, foreground-service delivery or OEM process policies.
