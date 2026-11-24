# Sandesh AI setup (0.5.0)

Sandesh uses Firebase AI Logic with the Gemini Developer API and `gemini-3.5-flash-lite`. It makes one text request per explicit action. There are no embedded Gemini secrets, autonomous replies, background inbox uploads, model tools or business grouping in this version.

## Owner setup

1. Select **Sandesh-SMS-App**, not another Firebase project. Finish **AI services → AI Logic → Get started → Gemini Developer API**.
2. Keep App Check enforced for AI Logic. Do not disable enforcement to make a development build work.
3. Configure the project's billing, provider quotas and any spending controls appropriate to your rollout. Review Google's terms and data-handling settings before processing real SMS. Budget alerts are notifications, not hard spending caps.
4. Leave AI monitoring/prompt capture disabled unless you have separately assessed and disclosed its handling of SMS content. Sandesh does not enable monitoring or analytics itself.

## Local debug builds

The debug source set installs the App Check debug provider. No debug secret is stored in source or supplied as a Gradle property.

1. Install a debug build, complete SMS setup and open **Settings → AI assistance**.
2. Tap **Check device verification**. This does not require enabling AI and does not share SMS or call Gemini.
3. If this installation is not registered, verification fails and the debug provider prints the token needed for registration.
4. In Android Studio Logcat, find the **DebugAppCheckProvider** message. Treat its debug token as a credential: keep it private, do not commit it, and do not paste it into issue reports or public chats.
5. Register the token in **Firebase → Security → App Check → Apps → com.sandesh.app → Manage debug tokens**. Retry the verification button after registration propagates.
6. Once verification succeeds, enable AI and test generation using only synthetic, non-sensitive messages. Review the text preview before sharing. Verification success alone does not prove the Gemini API, model access, billing or quota are ready.

Tokens are installation-specific. A fresh emulator, cleared application data or a different laptop/phone may require its own registration. The supplied Firebase configuration and OAuth signing fingerprints remain necessary for account sign-in; they do not replace App Check registration.

## Release builds

The release source set installs Play Integrity. The debug provider dependency is only in `debugImplementation`; no debug-token fallback is shipped in release.

Register `com.sandesh.app` with Play Integrity under Firebase App Check and configure the correct release signing SHA-256 fingerprints. For Google Play distribution, use the Play App Signing certificate rather than only the upload certificate. Check attestation requirements against your intended distribution, including physical phones and any sideload testing. Use a properly signed internal test build before release. An unsigned release APK/AAB cannot establish a production attestation setup.

## Behaviour and limits

- AI is off by default for existing and new installations. Summary and reply controls are independent. Turning off both hides conversation AI controls.
- Each action shows a text preview and sends only the selected conversation's prepared text. Sender aliases, addresses, SIM metadata, message IDs and timestamps are not added to the request. Identifying details can still occur inside message bodies.
- Local heuristics exclude likely OTP, verification, password, financial and long-number messages, including Unicode digits. They are intentionally conservative and incomplete; they are not a privacy guarantee or a spam classifier.
- Preparation examines the latest 50 messages, keeps up to 20 eligible messages in chronological order, caps each at 1,000 characters and caps all shared bodies at 6,000 characters. The UI identifies exclusions and truncation.
- One request at a time, a 45-second timeout, a 768-output-token ceiling, 10 seconds between generation attempts and 30 attempts per UTC day per installation. Failed/billed generation attempts count. App Check failures before generation do not. These client limits can reset or be bypassed; they are not a server-side quota or financial cap.
- Text and model answers are not saved by Sandesh or included in backups. The provider and project monitoring policies govern server processing/retention. Disabling AI or cancelling waiting cannot retract submitted text or guarantee that a request will not be billed.
- Switching conversations, blocking/spam state changes, loss of SMS access, disabling AI or restoring a backup cancels waiting and discards applicable results. Responses are bound to the prepared conversation fingerprint. A changed context requires generating again.
- Suggestions are inserted only through **Use as draft / Add to existing draft**. Existing draft text is preserved. Sending remains a separate user action using the normal SIM picker and SMS transport.
- AI enablement is device-specific and excluded from encrypted backups. Restore disables AI so the user must opt in again. Existing backup formats remain readable.

## Validation before enabling for other users

Use synthetic messages first. Verify both generation modes, all tones, cancellation, changing conversations while a request runs, toggles, offline/error states, sensitive-only threads, stale-response rejection and draft insertion without sending. Confirm server quotas and App Check reject unregistered installations. Test a signed Play internal release on a physical phone. Build/lint/unit tests alone do not establish live Firebase configuration, model availability, quota/billing setup or end-to-end generation.

References: [Firebase AI Logic setup](https://firebase.google.com/docs/ai-logic/get-started?platform=android), [App Check](https://firebase.google.com/docs/ai-logic/app-check), [structured responses](https://firebase.google.com/docs/ai-logic/generate-structured-output), [pricing and spending controls](https://firebase.google.com/docs/ai-logic/pricing).
