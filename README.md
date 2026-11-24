# Sandesh

Native Kotlin/Jetpack Compose SMS app, with an HTML design prototype kept as a reference.

## Open and build

Open the repository root in Android Studio, let Gradle sync, select **app**, and run on an Android phone (API 24+).

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:bundleRelease :core:test :app:lintDebug :app:lintRelease
```

Pinned toolchain: Gradle 9.6.0, AGP 9.5.1, Kotlin 2.2.10, Compose BOM 2025.08.01, Android SDK 37 and Build Tools 36.0.0. The SDK path belongs in ignored `local.properties`; Android Studio creates this per machine. The installed Android Studio JDK can run the build.

Outputs:

- Installable debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Optimized release APK: `app/build/outputs/apk/release/app-release-unsigned.apk` when no signing key is configured.
- Release bundle: `app/build/outputs/bundle/release/app-release.aab` (unsigned without a configured key).

Release signing uses environment variables; see [release preparation](docs/RELEASE.md). Never publish a debug-signed build.

## What is implemented

- Android default-SMS role onboarding, followed by SMS/SIM permission requests.
- Real SMS inbox from Android's system SMS provider, including unread state and per-message subscription IDs.
- Actual active SIM/eSIM detection. **Settings → Default sending SIM** supports a specific active SIM or **Ask every time**. The composer always shows the selected line. With multiple SIMs and no default, choose before every send. A removed saved SIM never silently falls back to another line.
- Real subscription-specific multipart SMS submission, persistent per-part result tracking, incoming SMS persistence and notifications. “Delivered” requires carrier delivery reports; it is never simulated. No automatic retries.
- SQLite app database for starred/archived flags, aliases, send-part receipts and pending incoming SMS. Actual SMS remains in Android's system store.
- Three saved themes: **Blue & white** (default), **Green & white**, and **Black & white**.
- A theme-tinted, transparent winged-envelope-and-hand logo, adaptive launcher icon and the supplied flying GIF as the splash. The supporting hand, envelope and animated wordmark are preserved. Playback follows Android's animation-duration setting.
- Search, unread/starred/archived folders, drafts during the current app process, external `sms:`/`smsto:` compose intents and optional offline reply examples.

## First launch

1. Read the SMS-only limitations on setup before changing your default app.
2. Choose Sandesh as the default SMS app, then grant SMS and phone-state access.
3. In Settings, enable notifications and choose a default sending SIM or Ask every time.
4. Choose your appearance. Messages stay local; there is no AI API connection.

No seeded conversations are loaded by the Android app. Upgrading the earlier prototype retains the same application ID; its old sample JSON is not imported into the real inbox. Uninstalling Sandesh removes app metadata, but does not delete SMS held by Android's system SMS provider.

## Important current limits

**This is a release candidate for physical-device testing, not a certified production release.** The build and unit tests do not establish carrier delivery or OEM behaviour.

- **SMS only. MMS attachment downloading/display, group MMS and RCS are not implemented.** Making Sandesh default can affect those features. MMS notification PDUs are retained and an explicit unsupported-MMS notification/banner is shown; attachments are not retrieved. Keep your previous messaging app installed.
- No cloud/export backup or restore yet. Android app backup/device transfer is excluded. SQLite is app-private storage, not separately password-encrypted.
- No contacts permission or automatic contact-name lookup; existing conversations show phone numbers, with optional aliases for conversations you create.
- AI replies remain clearly labelled offline sample text; draft tidying is local text rules, and summaries are local recaps.
- Unknown/removed historical subscriptions show **Unknown SIM** rather than an invented line.
- Telecom's “respond via message” service posts a review notification and opens a draft; it does not send silently. Notifications must be enabled for this flow.
- The current inbox loads SMS history on a background thread; very large inbox performance needs device testing and future paging.

See the [physical-device test plan](docs/DEVICE_TEST_PLAN.md), [privacy notes](docs/PRIVACY.md), and [branding and asset prompt](docs/BRANDING.md).

## Architecture

- `app/.../telephony`: role/permission checks, provider integration, selected-subscription sending, receiving and result notifications.
- `app/.../data`: SQLite metadata and pending receipts; private settings.
- `app/.../ui`: Compose screens, three themes, branding and splash.
- `SandeshViewModel`: inbox state, drafts, provider observation and background work.
- `core`: Android-independent domain models, SIM selection and multipart-status rules, offline reply interface and unit tests. Currently Kotlin/JVM, not KMP/iOS yet.

## HTML reference

Open `index.html` in a browser, or run `node server.cjs` and visit http://127.0.0.1:4173. This **browser prototype still uses fictional messages and simulated sending**. It has no access to your phone. The native Android app is the actual SMS implementation.
