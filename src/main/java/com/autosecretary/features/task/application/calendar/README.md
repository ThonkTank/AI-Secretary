# Task Calendar — Application Contract

This package defines the **public contract** for reading device calendar events from the application layer.

## Contents

| File | Role |
|---|---|
| `TaskCalendarService` | Interface: query device calendar events overlapping a time window |
| `ScheduleWindow` | DTO: a (day, startTime, endTime) record used as the query parameter |

## Why a separate package?

Calendar integration surfaces in two distinct contexts with different needs:

1. **Application layer** (`this package`) — UI and slot generation need *rich* event data (event titles, convenient types). `ScheduleWindow` bundles day + time range into one readable parameter; `TaskCalendarService` returns `TaskCalendarEvent` objects with display titles.

2. **Domain layer** — the scheduling algorithm needs only *minimal* data (start/end times). That contract (`CalendarBlockedIntervalProvider`) and its Android implementation live in `domain/scheduling/` and `internal/calendar/` respectively.

This separation avoids coupling UI concerns to domain algorithm design.

## Implementations

See [`internal/calendar/`](../internal/calendar/README.md) for the Android implementations:

- `CalendarReader` — implements `TaskCalendarService` for the application layer
- `DeviceCalendarBlockedIntervalProvider` — implements `CalendarBlockedIntervalProvider` for the domain layer
- `CalendarQueryHelper` — shared Android Calendar query logic used by both

## Reading order

1. Read `TaskCalendarService` for the contract.
2. Read `ScheduleWindow` to understand the query parameter.
3. Go to `internal/calendar/CalendarReader` to see the Android implementation.
