# Sandesh privacy notes (0.3.0)

This documents implemented behaviour. The owner must supply a hosted policy, support contact and retention/deletion terms before publication.

Sandesh reads and writes SMS after you make it the default SMS app and grant permissions. Phone-state access identifies active SIMs. Carrier SMS travels through your mobile operator to the recipient when you tap Send. Notifications use a generic message preview.

SMS is held in Android's system SMS provider. Sandesh's private SQLite database stores stars, archives, aliases, send/delivery receipts, pending incoming SMS and raw unsupported MMS notifications. These records can contain message content. Preferences store appearance and sending choices. Android protects this local storage; the backup password does not encrypt the running app's database. Automatic Android app-data backup/device transfer remains excluded.

Accounts are optional. Email/password registration, login, verification, password reset and Google sign-in use Firebase Authentication and Android Credential Manager. Firebase receives account information such as email, optional name, provider identity and authentication requests. Sandesh does not put account passwords or authentication tokens into SMS backups. Firebase and Google SDKs manage authentication sessions; passwords are not stored in Sandesh preferences. Internet access supports these optional features. No advertising, analytics or cloud AI SDK is included.

File backups are created only when requested. SMS, thread stars/archives/aliases, labels and appearance are compressed and encrypted on the phone using AES-256-GCM. PBKDF2-HMAC-SHA256 with a fresh salt and 600,000 iterations derives the key from an independent backup password. A new nonce is used for each backup. The selected Android document provider receives the encrypted file; that provider may be local storage or a cloud service chosen by you.

Cloud backup is also manual and uses the same device-side encryption. Firebase Cloud Firestore stores encrypted chunks and operational metadata (account UID, size, chunk count, digest and timestamp). Private rules restrict access to the owning verified account. A successful upload publishes a new latest snapshot and attempts to remove the preceding one. Interrupted uploads or failed cleanup can leave older encrypted records. No automatic expiry or background backup is implemented. The owner must deploy the private rules described in FIREBASE_SETUP.md before enabling cloud backup.

Backup passwords are not uploaded. Losing that password makes the backup unrecoverable; resetting your account password does not decrypt it. Backup encryption does not hide account identity, backup size or timestamps from the service operator. Backups exclude MMS/RCS, process-only drafts, pending send jobs and account credentials.

Restore requires default-SMS access, full password/integrity/format verification and confirmation. It merges history without deleting current SMS. Restored unsent messages are marked failed and never submitted for sending. Historical SIM IDs are not reused on a new phone.

Uninstalling removes app-private data and local authentication state, but does not delete system SMS, exported files, your Firebase account or cloud backups. Signing out also retains those records. Account/cloud-data deletion is not yet available in this candidate; the owner must implement an in-app deletion flow and a public deletion-request route before a store release with accounts.

Contacts access, MMS attachment transport, group MMS and RCS are unsupported. AI reply suggestions remain offline sample text and local rules. Android, carriers, document providers, Google and Firebase have their own data handling outside Sandesh's local storage controls.
