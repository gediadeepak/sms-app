# Sandesh mini inbox for Sony SmartWatch 3 (0.8.0)

The `wear` module is a separate native Android application for the connected Sony SWR50: Android 6.0.1 / API 23, build M1D64T, Google Play services 8.7.01. It has a Sandesh launcher icon using the existing winged-envelope-and-hand logo. This is an app-sized inbox for Android Wear 1.5, not a modern Wear OS tile or a phone homescreen widget.

## Install and use

Build `:app:assembleDebug :wear:assembleDebug`. The **phone APK** is `app/build/outputs/apk/debug/app-debug.apk`. The **watch APK** is `wear/build/outputs/apk/debug/wear-debug.apk`. Install each on the correct device; both use application ID `com.sandesh.app` and must have the same signing certificate. Do not install the watch APK over the phone application. This version uses USB sideloading for the watch; no automatic wearable-APK embedding is configured.

After installing/updating both apps, open the phone's **Sandesh → Settings → Smartwatch → Smartwatch settings → Enable watch inbox**. This separate opt-in starts off. Launch Sandesh from the watch's apps list and tap Refresh. It displays ten recent non-archived, non-Spam/non-blocked conversations. Tap one for the latest five message previews, bounded to 480 characters each. Sender names follow optional phone Contacts permission, then alias/number fallback. The watch follows the phone's six theme choices when receiving a response.

**Open on phone** requests a phone-only notification. Tap that notification on the phone to open the exact conversation, even outside its loaded inbox page. It does not start an activity from a background service, change drafts/SIM selection or send an SMS. Watch reads do not mark phone messages read. Existing mirrored notification actions remain separate.

## Transport and storage

The watch deliberately uses the legacy `play-services-wearable:8.4.0` GoogleApiClient/MessageApi, compatible with the actual installed 8.7 services. The phone uses `play-services-wearable:20.0.1`. Their classpaths are separate; no legacy SDK is added to the phone. They share a tiny pure-Java, versioned protocol with bounded UTF-8 requests/responses, request correlation and numeric thread validation. The watch has no SMS, Contacts or account permissions and never invokes the carrier SMS transport or Gemini.

Google Play services authenticates matching package/signature peers. Messages may travel over Bluetooth or Google's relay network according to the companion connection. This is not a Bluetooth-only guarantee. No DataClient/DataItems, persistent watch message database, disk cache, automatic background sync or watch account is used. Responses are requested only while the watch app is in the foreground. Preview text is cleared on pause/exit and after one minute. Screenshots are disabled for this app. Changing phone watch settings sends a best-effort clear message; disconnected watches cannot be guaranteed to clear immediately, so the local expiry still applies. SMS access and the opt-in are checked again before a reply is sent.

## Validation and limits

Use `:core:test :watch-protocol:test :app:lintDebug :app:lintRelease :wear:lintDebug :wear:lintRelease` and builds for each module. Protocol tests cover malformed IDs, forbidden send commands, complete emoji clipping and UTF-8 byte limits. Check both APK certificates and package versions before installing. The legacy watch intentionally targets API 23; this APK is for the old Sony device, not a modern Play Store watch release.

The initial paired-device checks are recorded in [WATCH_DEVICE_RESULTS.md](WATCH_DEVICE_RESULTS.md), including real inbox/thread responses and the phone-notification handoff.

On the connected pair, verify launcher visibility, a successful disabled-setting response, no private payload in logs, enable/disable, ten-row inbox, five-row thread, Refresh, Open on phone, navigation, all six themes, process/screen-off expiry, phone permission/default-role loss, Spam/archive/block handling and disconnect/reconnect. Diagnostic logs contain command/status only, never SMS bodies or sender details. A successful build is not proof of end-to-end sync; record actual device results separately.

References: [Google Data Layer overview and app identity requirements](https://developer.android.com/training/wearables/data/overview), [client behaviour](https://developer.android.com/training/wearables/data/client-types).
