# Privacy and portable data

## Local storage

The Android app keeps medication schedules, stock, recorded entries, snoozes, and alarm identities in its application-private Room database. Preferences use DataStore. There is no account, analytics, advertising SDK, remote API, or cloud sync. The app does not request the Android `INTERNET` permission.

The database is not independently encrypted by MediFlow. It relies on Android's application sandbox and device storage protection. Automatic Android backup is disabled. Device transfers, rooted devices, operating-system behavior, and the security of user-selected export destinations are outside the app's control.

Notification text is discreet by default. Enabling detailed text exposes the medication name in unlocked notifications; lock-screen visibility remains private.

## Export and restore

The system document picker lets the user choose an export or import location without broad storage permission. A JSON export contains the complete medication list, including archived entries, and recorded history. It can include sensitive information and is not password protected. No file is exported automatically.

Backup version 1 has four top-level fields:

| Field | Meaning |
| --- | --- |
| `version` | Format version, currently `1` |
| `exportedAt` | ISO-8601 export timestamp |
| `medications` | IDs, labels, exact inventory quantities, schedule fields, notes, colors, archive flags, and revision numbers |
| `records` | Occurrence keys, medication IDs/names, scheduled/recorded timestamps, historical quantities, and status |

Record timestamps are epoch milliseconds. Medication quantities are integer thousandths of a unit. Weekdays use ISO numbers 1–7; times are comma-separated 24-hour values. IANA time zones are validated.

Imports are limited to 2 MiB, 500 medications, and 20,000 records. Unknown versions, duplicate IDs/keys, missing references, malformed schedules, and invalid quantities are rejected before the database replacement. Restore is explicit and replaces the current routine; it does not merge two routines.

Appearance, permission grants, Android preferences, and pending snoozes are not included in a backup. Restoring a routine does not grant notification permissions. Existing alarm identities are retained long enough for the scheduler to cancel stale PendingIntents.

## Browser companion

The self-contained companion stores preview data in the browser's local storage. It has no network calls, cookies, telemetry, or notification service. Clearing the browser's site data removes that copy. If local storage is unavailable, it reports that changes last only for the current session.

The companion and Android app share a backup format, not a synchronization service. Export and import are deliberate user actions. All included sample entries and history are fictional.
