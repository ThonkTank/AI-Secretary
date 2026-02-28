# Task Schedule Configuration

Manages the **per-day scheduling window** — the time boundaries within which task slots can be generated (e.g., "8am to 11pm").

## What it does

`TaskScheduleConfigRepository` implements `SchedulingWindowProvider`, allowing task scheduling windows to vary per day of the week. Instead of using a fixed app-wide window (see `SchedulingWindowProvider.DEFAULT`), users can configure different start/end times for each day.

## How it fits in scheduling

During slot generation, the scheduler queries the `SchedulingWindowProvider` (this repository) for the allowable time window for each day. Slots placed outside this window are rejected.

## Related

- **SchedulingWindowProvider** — the interface contract: returns a SchedulingWindow for a given date
- **TaskScheduleConfigDao** — the persistence layer (Room entity)
- **TaskPrefSlotFactory** — defines app-wide default times (8am–11pm by default)

## For novices

If you're implementing per-day scheduling windows:
1. Read `SchedulingWindowProvider` first to understand the contract
2. Then read `TaskScheduleConfigRepository` to see how it persists and caches daily configs
3. See `TaskScheduleConfigDialog` (UI layer) for how users edit these times

The cache behavior is important: the repository lazily loads from the database and invalidates the cache only when `saveAll()` is called. This is documented in the class javadoc.
