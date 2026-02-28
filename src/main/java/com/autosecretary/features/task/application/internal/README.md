# `application/internal/` — Android & Infrastructure Implementations

This directory contains Android-specific and infrastructure implementations of contracts defined in the `application/` layer. Nothing here is part of the public application API; it is all "wiring" that connects domain logic to the Android runtime.

## What "internal" means here

The `internal/` packages hold concrete implementations that:
- Depend on Android framework classes (`AlarmManager`, `CalendarContract`, `RoomDatabase`, etc.)
- Are registered in `AndroidManifest.xml` (receivers), or wired through `AppCompositionRoot`
- Should not be referenced directly by UI or domain code — only through the interface they implement

If you are new to this codebase, start reading from the interfaces in `application/` and `domain/scheduling/`, then come here to see how they are implemented on Android.

## Sub-packages

| Package | Purpose |
|---------|---------|
| [`alarms/`](alarms/README.md) | Daily midnight alarm: `BootReceiver`, `DailyPlanningScheduler`, `DailyPlanningReceiver` |
| [`budget/`](budget/README.md) | Task-budget lifecycle: eligibility check during scheduling and expense booking on completion |
| [`calendar/`](calendar/README.md) | Android calendar integration: reads device events as scheduling conflicts or blocked intervals |
| [`mutations/`](mutations/README.md) | Atomic multi-DAO write operations, starting with `TaskSlotToggleMutation` (two-phase check-off) |

## `TaskSeedDataFactory` (this directory)

[`TaskSeedDataFactory.java`](TaskSeedDataFactory.java) populates the database with demo tasks the first time the app runs on a fresh install (empty database). It is called by `RegenerateScheduleUseCase` when no tasks are found.

**Note:** All seed task names are in **German** — this is intentional. The app's user-facing language is German throughout (see `CLAUDE.md` Conventions section). The seed data is illustrative only and not shipped as production content.

The class uses an internal fluent `TaskBuilder` to reduce boilerplate when creating tasks with many optional fields. It is not part of the application API and is never called after the initial seed run.
