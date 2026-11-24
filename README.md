# Relay — native Android app and HTML prototype

## Android Studio project

Open **this repository root (`G:\Project\SMS-App`)** in Android Studio using **File → Open**. Wait for Gradle sync, choose the **app** run configuration, select an emulator or Android phone running Android 7.0/API 24 or newer, then press Run.

The native app is written in **Kotlin and Jetpack Compose**, using the Relay HTML prototype as the visual reference. It includes the sage/green palette, inbox and conversation layouts, per-message SIM badges, settings, starred/archived/unread folders, search, new conversations and the offline assistant. Phones show one screen at a time; wider windows show inbox and thread together, with a persistent assistant on large screens.

**This first Android milestone is an offline demo.** It uses sample SIM labels and fictional numbers. It does not send real SMS, read the system SMS database, request phone/SMS permissions, or call an AI provider. The send/delivery indicators are simulated. Messages and preferences persist locally in app-private storage. Drafts survive activity rotation but not process death. Uninstalling the app or resetting demo data removes the local inbox. Android backup is disabled for this demo.

### Build requirements

- Android Studio with its embedded JDK (the installed JDK 25 is supported by Gradle 9.6).
- Android SDK Platform 37 and Build Tools 36.0.0.
- Gradle 9.6.0 via the checked-in wrapper; Android Gradle Plugin 9.5.1.
- Kotlin/Compose compiler 2.2.10, matching the Kotlin bundled with this AGP; Compose BOM 2025.08.01.
- Internet access for the initial dependency download.

Your local SDK location is written to **ignored** `local.properties`. Other machines should let Android Studio generate that file. Set Android Studio's **Gradle JDK** to its embedded JDK if prompted. A separate global Gradle or Java installation is unnecessary.

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:assembleDebug :core:test :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Native architecture

- `app/`: Compose UI, ViewModel, local Android persistence and the launcher activity.
- `core/`: Android-independent Kotlin models, filtering/validation rules, sample inbox and the `ReplyAssistant` interface, with JVM unit tests.
- `gradle/`: pinned dependencies and the Gradle wrapper.

`core` is currently a Kotlin/JVM module, **not yet Kotlin Multiplatform**. Its source avoids Android APIs so the business logic can be migrated into KMP common code when iOS work begins. iOS targets, SwiftUI and Xcode setup are not included in this Android milestone.

### Next native milestones

1. Implement the Android default-SMS role and permission flow, plus a platform subscription adapter for actual SIMs/eSIMs.
2. Implement inbox-provider integration, receiving, notification channels and subscription-specific sending with real result callbacks.
3. Replace the small demo JSON store with Room for production inbox state and add physical dual-SIM device coverage.
4. Add an authenticated backend for optional AI, with explicit sharing controls; keep provider secrets off the phone.
5. Extract the common domain code into Kotlin Multiplatform and implement the permitted iOS experience.

## Original HTML prototype

A responsive, local prototype for the dual-SIM SMS app. Built with HTML, CSS and JavaScript; no build step, package installation or API keys required.

## Open it

Open `index.html` in a modern browser, or run `node server.cjs` and visit http://127.0.0.1:4173.

## Try it

- Open a conversation and send a demo SMS using either SIM.
- Filter by personal/work line, search names, numbers or message text, and browse unread, starred and archived conversations.
- Star or archive a conversation; restore it from Archived.
- Start a conversation with **New message** (`N`). Focus search with `/`.
- Open the sample assistant, choose a tone, insert a reply, or view a local conversation recap.
- Use the sparkle beside Send to tidy your draft using simple local rules.
- Rename SIMs, choose a default line or disable suggestions in Settings.
- Use Settings → Simulate an incoming message to exercise unread states.
- Reload to keep your messages and preferences. Settings → Reset demo conversations restores sample data.
- On a phone, select a conversation to open its thread and use Back to return to the inbox.

## Prototype boundaries

**No real SMS is sent, no phone inbox is accessed and no AI service is connected.** Sending and delivery states are simulated. Reply ideas are preset examples selected by the sample conversation topic and tone. The summary is a local recap and draft polishing uses local rules. New conversations use generic reply ideas.

Messages and settings are stored in this browser's localStorage, without a server database. Drafts last for the current session. Private browsing or disabled/full browser storage may prevent persistence. Serving over localhost is preferable for consistent storage behaviour. Opening as a file and opening via localhost use separate storage.

The only external resource is optional Google Fonts; system fonts are used if unavailable. There are no external JavaScript dependencies. All sample contact numbers are fictional UK drama-range numbers.

Android Studio does not run this HTML prototype as a native Android application. It is a reviewable model of the future app's user experience.

## Files

- `index.html` — app structure, forms and inline SVG icons
- `styles.css` — responsive desktop/mobile layout
- `app.js` — demo state, local persistence and interactions
- `server.cjs` — optional dependency-free localhost preview server
