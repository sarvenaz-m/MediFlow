# Reminder behavior

MediFlow schedules reminders from the times entered by the user. Reminders are disabled initially, including for fictional sample routines.

## Permissions and delivery

| Condition | Behavior |
| --- | --- |
| Notifications permitted, exact-alarm access granted | One-shot `setExactAndAllowWhileIdle` alarms |
| Notifications permitted, exact-alarm access absent | `setAndAllowWhileIdle` fallback; delivery may be delayed |
| Notifications denied or channel disabled | No new reminder alarms; obsolete alarms are cancelled |
| Exact access revoked between checking and scheduling | Security exception is handled and an approximate alarm is requested |
| Reminders switched off | Scheduled alarms and posted routine notifications are cancelled |
| Record taken or skipped | The notification is dismissed and the upcoming schedule is reconciled |
| Snooze | A persisted ten-minute snooze replaces that occurrence's alarm |

Android 13+ notification permission and Android 12+ exact-alarm special access are separate. Settings displays their current states and opens the relevant Android controls. Exact-alarm access is requested through the system settings screen, not assumed.

Notification actions use immutable PendingIntents with explicit receiver components and occurrence/action-specific URIs. Medication names are hidden by default. The lock-screen visibility is private.

## Time, travel, and restart

- **Follow device clock:** an 08:00 entry follows 08:00 in the current device zone.
- **Keep home time:** the entry follows its configured home zone while the display shows the corresponding device-local time.
- **DST:** nonexistent times move forward by the transition gap; repeated times produce one entry using the earlier offset.
- **Reboot or clock/zone change:** manifest receivers rebuild the schedule from local storage.
- **App update or foreground resume:** reconciliation removes stale entries and refreshes platform capabilities.
- **Maintenance:** a periodic WorkManager request extends the scheduling horizon. The operating system can defer it.

Closing the activity or swiping it from recent apps is different from force-stopping the package. Android can suppress alarms and background work after a force-stop until the app is opened again. Device battery controls and vendor-specific restrictions can also delay delivery. The app does not claim guaranteed delivery during these conditions.

## Device validation procedure

Before treating a distribution as device-validated, check the following on representative physical Android versions:

1. Enable notifications, create an entry a few minutes ahead, background the activity, and observe delivery and the notification actions.
2. Repeat without exact-alarm access; confirm the UI reports approximate delivery.
3. Disable the channel and revoke notification permission; confirm the app reports the changed state.
4. Create a snooze, restart the app process, and verify it is retained.
5. Reboot, change the device time zone, and edit/archive the medication; confirm obsolete reminders disappear.
6. Compare swipe-away with force-stop and reopening. Test battery restrictions and idle conditions separately.

Host tests verify scheduling decisions and identifiers. They do not measure real notification latency or device-specific power behavior. See the [verification record](VERIFICATION.md) for what has actually run.

Platform references: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule), [notification permission](https://developer.android.com/develop/ui/views/notifications/notification-permission), [WorkManager configuration](https://developer.android.com/develop/background-work/background-tasks/persistent/configuration/custom-configuration).
