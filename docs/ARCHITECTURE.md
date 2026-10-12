# Architecture

MediFlow separates schedule and inventory rules from Android storage, reminders, and presentation. The app is a single-activity Compose application with four top-level destinations. It does not require a backend.

## Dependency direction

`feature:routine` depends on `core:model` and `core:designsystem`. `core:data` and `core:reminders` implement ports from `core:model`. The reminder module does not depend on Room: it stores scheduled alarm identities through `AlarmRegistry`. `app` assembles the concrete implementations with Hilt.

`core:model` is a JVM module without Android APIs. The browser companion has a separate JavaScript domain implementation and matching edge-case tests; it does not execute the Kotlin engine. Both implementations read and write the version 1 backup format.

## State and mutations

Room is the source of truth for medications, records, snoozes, and the alarm registry. DataStore contains appearance, onboarding, privacy, and reminder preferences. The ViewModel combines repository flows with selected date, clock ticks, and current capabilities into a `StateFlow<RoutineUiState>`. Compose observes that state with lifecycle awareness.

Database mutations are suspend operations. The presentation layer never subtracts inventory locally. Room transactions atomically validate and update stock together with a dose record. Editors retain a snapshot revision: if another operation changes the medication while the editor is open, saving that stale draft is rejected instead of overwriting new stock.

Transient editor drafts survive activity recreation through saved instance state, including the original revision. Persistent history remains in Room. Large backup content is not placed in a saved-state Bundle.

## Invariants

| Concern | Rule |
| --- | --- |
| Quantity | Integer thousandths of a unit, bounded at 1,000,000,000 milli-units; no floating-point stock arithmetic |
| Occurrence | `medicationId\|schedule-local-date\|HH:mm`; identity follows the logical entry across time-zone changes |
| Repeated action | A record key is unique; duplicate recording cannot subtract stock twice |
| Undo | Uses the quantity captured in the record, even if the medication was later edited |
| Inventory | Cannot become negative; an insufficient-stock action leaves the transaction unchanged |
| Archiving | Stops future recurrence and reminders, retains recorded history |
| Historical display | Uses record snapshots for name, quantity, scheduled instant, and recorded instant |
| Insights | Counts recorded entries, not reconstructed historical adherence from today's schedule |
| Backups | Validate the whole payload before one atomic replacement; invalid files leave the routine untouched |

## Schedule semantics

A schedule contains minute-precision times, selected weekdays, inclusive start/end dates, a time policy, and an IANA home-zone identifier. Day queries convert device-day boundaries to instants and resolve each recurrence in the schedule's zone. Date ranges use half-open intervals, preventing duplicate boundary entries.

For a nonexistent local time during a spring DST transition, `java.time` moves the occurrence forward by the gap. During a repeated autumn hour, the earlier offset is selected once. The tests include half-hour transitions and travel between time zones.

Schedules are recurring local times, not fixed-hour dosage intervals. Editing a schedule affects future planning and the current schedule view; stored records preserve what was actually logged. The app does not infer missed-dose instructions.

## Reminder reconciliation

The scheduler derives an eight-day horizon from the current routine. Persisted snoozes override individual occurrences. It cancels obsolete PendingIntents, schedules desired entries, and persists identities through `AlarmRegistry`. A mutex serializes reconciliation within the process.

A delivered notification is retained after its alarm has fired. It is removed when its entry is recorded, becomes invalid or archived, or reminders are disabled. WorkManager maintains the horizon and retries failed reconciliation. It is not the delivery mechanism for a precisely timed entry.

WorkManager uses application-provided configuration. Workers obtain the scheduler from an application Hilt entry point, so persisted worker requests remain constructible after process death.

## Evolution

Room schema version 1 is exported under `core/data/schemas`. There is no destructive migration fallback. A later schema change should add an explicit migration and a migration test. Backup schema versioning is independent of the Room schema.

The benchmark module contains runnable cold-start and profile-collection journeys. A generated baseline profile or a device performance claim should only be added after measuring on a representative device.
