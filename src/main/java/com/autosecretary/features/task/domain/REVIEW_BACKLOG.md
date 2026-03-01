# Review Backlog — task/domain

## Open Issues

### [consider] TaskCalendarEvent is a redundant type alongside BlockedInterval
**File:** `TaskCalendarEvent.java` and `DefaultTaskSlotGenerator.java` (package-private overload)

`TaskCalendarEvent(String title, LocalTime start, LocalTime end)` is a domain record created by `CalendarReader` (in `application/internal/calendar/`) and passed to the package-private `generateSlotsForDay` overload in `DefaultTaskSlotGenerator`. Inside `collectOccupiedIntervals`, these events are immediately converted to `OccupiedInterval` via `day.atTime(event.start())` — the `title` field is not used in any logic.

The type is nearly equivalent to `CalendarBlockedIntervalProvider.BlockedInterval(LocalDateTime start, LocalDateTime end)`, differing only in using `LocalTime` vs `LocalDateTime` fields and having a `title` field (unused in scheduling).

**Simpler alternative:** Have `CalendarReader` produce `BlockedInterval` objects directly (using the known day to construct `LocalDateTime`), and change the package-private test overload to accept `List<CalendarBlockedIntervalProvider.BlockedInterval>` instead of `List<TaskCalendarEvent>`. This eliminates the intermediate type and the conversion in `collectOccupiedIntervals`. The `title` field would be lost, but it is not used for any scheduling logic.

**Why deferred:** Requires changes in `application/internal/calendar/CalendarReader` and its callers outside the domain package. Investigate `TaskCalendarService` usages before removing.

---

