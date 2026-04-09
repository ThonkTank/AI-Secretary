# Calendar Module

This module provides Android calendar integration for task scheduling.

## Overview

Two calendar implementations serve different purposes:

### `CalendarReader` (Application-facing)
- **Purpose:** Read calendar events for display and high-level scheduling decisions
- **Used by:** Task list UI, slot generation initialization
- **Returns:** `TaskCalendarEvent` (includes event title, user-facing information)
- **Parameter:** `ScheduleWindow` (convenience record: day + time range)
- **Output:** Events clamped to schedule bounds with display titles

**Example:** "Show calendar conflicts in today's task list between 9:00–17:00"

### `DeviceCalendarBlockedIntervalProvider` (Domain-facing)
- **Purpose:** Find blocked time intervals for scheduling algorithms (scoring, feasibility)
- **Used by:** Slot scoring, slot filtering, scheduling validation
- **Returns:** `BlockedInterval` (minimal: just start and end times)
- **Parameters:** day, windowStart, windowEnd (explicit, lower-level API)
- **Output:** Time intervals blocked by calendar events, clamped to window

**Example:** "Mark 14:00–14:30 as blocked when scoring potential time slots"

## Architecture

**Why two implementations?**

The separation reflects the different needs of application vs. domain layers:

- **Application layer** (TaskCalendarService) is UI-facing, needs rich event data (titles) and convenient parameter types (ScheduleWindow)
- **Domain layer** (CalendarBlockedIntervalProvider) is algorithm-facing, needs minimal data (just times) and explicit parameters for composability in scheduling logic

This avoids tight coupling between UI concerns and domain algorithm design.

## Permissions

Both implementations require `android.permission.READ_CALENDAR`.

**If permission is missing:** Both return empty results (no blocking data, no conflicts shown).

**Who requests permission?** The UI layer. Applications using these services should request the permission before invoking them.

## Implementation Notes

- Both implementations use Android's `CalendarContract.Instances` API — **not** `CalendarContract.Events`. This distinction matters: `Events` stores raw event definitions, while `Instances` expands recurring events into individual occurrences. Using `Instances` ensures a recurring "Weekly Meeting" appears as a concrete entry on each relevant day, which is the correct input for daily scheduling decisions. See the [calendar provider guide](https://developer.android.com/guide/topics/providers/calendar-provider#query) for details.
- Event times are converted from epoch milliseconds to `LocalTime`/`LocalDateTime`
- **All-day events are intentionally filtered out.** Events like birthdays and public holidays span the entire day but don't represent a specific time block. Task scheduling only cares about timed conflicts, so all-day events are skipped by design. If ever needed, the `allDay` column check would need to be removed and the event mapped to a full-day interval.
- Returned intervals are always clamped to the requested window
- Results are sorted by start time (ascending)

## Public Resources

- [Android CalendarContract documentation](https://developer.android.com/reference/android/provider/CalendarContract)
- [Reading calendar events](https://developer.android.com/guide/topics/providers/calendar-provider)
- [Requesting runtime permissions](https://developer.android.com/training/permissions/requesting)
