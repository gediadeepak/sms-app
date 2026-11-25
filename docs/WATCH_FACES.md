# Sandesh watch faces

Open **Sandesh → Settings → Smartwatch → Smartwatch settings → Create & apply watch faces** on the phone. Install both APKs from the same release; the watch APK belongs on the Sony watch, not the phone.

Refresh the watch gallery, choose a saved design or New face, enter a name, select style/color and display options, then Save to watch or Apply on watch. The first application opens the watch's system face picker for confirmation. Once Sandesh is the current face, later Apply requests update its design. You can also long-press the watch clock and choose Sandesh. The watch's Sandesh launcher has a Faces button for offline editing.

- **Retro LCD**: display-only LCD with weekday/date, segmented hours/minutes, optional seconds, actual watch battery and year. No branding or footer text.
- **Retro Dual**: upper seconds panel, circular weekday/date dial and a large lower clock. SMS and Faces footer shortcuts open the corresponding watch screens. This is a local-time clock, without world-time, alarm or stopwatch functions.
- Digital, analog and minimal styles; six colors; date, battery, seconds and 12/24-hour options. All styles use a white-on-black ambient clock with no seconds updates while asleep.

Save does not apply the face. Up to twelve named designs are kept privately on the watch, with an independent active snapshot. Apply another design before deleting the active one. There is no cloud face account or SMS content on the clock. Deleting app data removes the gallery.

The renderer is shared between phone previews and the watch. Drawing is clipped to preview bounds. The launcher uses a 128px unscaled icon to avoid exhausting the Sony launcher's small heap. The legacy SDK and listener declaration support Google Play services 8.7 on Sony SmartWatch 3. This is a legacy Sony test build, not a modern Wear OS Play Store submission.

Face requests use separate versioned Data Layer paths, UUID correlation, connected peer validation, bounded payloads, validated fields and timeouts. Google Play services requires matching package names and signatures. The face service ignores other paths; SMS inbox opt-in remains separate.

A G-TiDE R1 is a different platform. Its GT FIT Pro companion workflow does not establish support for installing this APK or these faces.
