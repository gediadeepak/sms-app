# Lazy SMS loading (0.7.0)

The inbox initially loads 50 conversation previews, then another 50 near the bottom. A conversation starts with its latest 50 SMS; scrolling up loads 50 older messages. Stable message keys and reverse-layout history avoid forcing a jump to the bottom when adding older pages. There is also an explicit Load more / Retry button.

Android's SMS provider remains the source of truth. The full-history inbox loader has been removed; no new message database or schema migration is added. SQL selects the latest non-draft SMS per thread, using numeric IDs to break equal-date ties. Date/ID keyset cursors avoid offset drift. Provider selections impose numeric limits: 100 inbox candidates per query (to fill pages after app-private folder/block filters), the requested history window plus one, or one direct thread preview. Provider SQL still performs grouping/search; the app no longer materializes every SMS to group it.

Refresh reconciles only the current requested windows. Closing a thread releases older bodies and reopening starts at 50; the process-only draft and SIM choice stay intact. Superseded queries receive cancellation signals. Folder/SIM/search changes restart at 50. The header reports loaded counts and `+` when more exist; global sidebar unread/spam totals are omitted instead of showing partial counts.

Search includes unloaded older text through distinct matching provider thread IDs, plus sender/contact names. Intentional search can scan older text inside Android's database without copying those bodies into the app. Notification/watch links fetch the exact thread even beyond page one. Block/unblock queries distinct sender/thread identities so it covers unloaded threads too. Full manual backup/restore remains complete. AI waits for loaded history and still uses only its latest 50 source messages; loading older pages preserves that context fingerprint.

## Tests and device checks

Run core Gradle tests and `node --no-warnings tools/Test-SmsPaging.cjs` (Node with built-in SQLite). Synthetic SQL tests cover 153 conversations, 127 history rows, 50-row pages, equal timestamps, restored old rows with larger IDs, excluded drafts, insertion/deletion at boundaries, older-body search, unread counts and empty/exact/extra-row boundaries. They read no device SMS.

On the owner's phone, test cold launch with a large inbox, successive inbox/history pages, scroll position, new SMS and delivery changes while paging, rapid search/folder/SIM changes, contacts and old-body searches outside page one, notification/watch links to unloaded threads, errors/retry, permission/default-role changes, drafts/SIM preservation, Spam/block/archive/star beyond page one, and full encrypted backup/restore. Carrier behaviour and UI performance are not established by synthetic tests. Vendor providers that restrict default-handler SQL subqueries require device validation; failures are shown instead of silently falling back to a full-history read.
