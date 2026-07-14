# Task Feature (`features/task`)

## What this feature does

The **task feature** is the core of AutoSecretary. It lets users define recurring or one-off tasks
(e.g. "Read 30 minutes", "Therapist appointment"), then automatically generates a day-by-day
schedule of concrete time blocks ("slots") based on each task's preferred times, priorities,
cooldown rules, and calendar conflicts.

**Key user flows:**
- Create / edit tasks with priority, repetition, preferred times, deadline, and optional budget requirement
- View today's scheduled slots in the checklist (sorted by time)
- Check off slots in two taps (first tap = start, second tap = complete)
- See all tasks in manage mode (grouped by category)
- Navigate up to 6 days forward; widget on the home screen mirrors the daily view

## Layer map

```
features/task/
├── data/          DAOs and DAO-near persistence types. No business logic here.
│
├── domain/        Core logic: completion service, lifecycle (streaks, periods, adaptive times),
│                  tree operations, and scheduling contracts + implementation.
│                  model/ contains the task aggregate and Room-annotated task model.
│                  No Android dependencies.
│
├── application/   Use cases that orchestrate domain services and data access.
│                  Provides TaskDataService, RegenerateScheduleUseCase, CheckOffTaskUseCase, etc.
│                  Android-specific implementations (alarms, calendar, mutations) live in
│                  application/internal/.
│
└── ui/            Fragments, ViewModels, adapters, dialogs.
                   list/    — main task list and checklist screen
                   edit/    — task create/edit dialog
                   widget/  — home-screen widget
```

## Recommended reading path for new contributors

1. **[`CLAUDE.md` — Glossary section](../../../../../../../../CLAUDE.md)** — Read the glossary first.
   Terms like "Slot", "PrefSlot", "Repetition", "Period", "Streak", and "Adaptive" appear everywhere.

2. **[`domain/README.md`](domain/README.md)** — Understand the task model and core behavior before anything else.
   Read `domain/model/Task.java` and `domain/model/TaskCore.java` to see all the fields.

3. **[`data/README.md`](data/README.md)** — Understand how Room DAOs persist the domain model.

4. **[`domain/README.md`](domain/README.md)** — Understand two-phase check-off and period/streak tracking.
   Then follow its reading order into `domain/scheduling/README.md` for the scheduling algorithm.

5. **[`application/README.md`](application/README.md)** — Understand how use cases wire domain services to data access.
   `RegenerateScheduleUseCase` is the most important entry point for the scheduling pipeline.

6. **[`ui/list/README.md`](ui/list/README.md)** — Understand the main list screen (data flow, two modes, day navigation).

7. **[`ui/edit/README.md`](ui/edit/README.md)** — Understand the task edit dialog.

8. **[`ui/widget/README.md`](ui/widget/README.md)** — Understand the home-screen widget (RemoteViews pattern).

## Key architectural choices

- **`Task` is a Room POJO, not a `@Entity`.** Room assembles it from six tables via `@Relation`. Only `TaskCore` is the primary task entity. The model lives in `domain/model`; `data/` owns the DAOs that persist it.
- **Current persistence shape.** DAOs are still used directly from some use cases and mutations. Treat this as existing app shape unless a focused change moves a specific call path.
- **DB executor.** All task DB access runs on `AppCompositionRoot.getDbExecutor()`; file/network work uses the separate `ioExecutor`. Results post to main via `Handler`.
- **Daily alarm.** Schedule regeneration runs at midnight via `AlarmManager`. See `application/internal/alarms/`.

## Public resources

- [Android Room overview](https://developer.android.com/training/data-storage/room) — persistence library used throughout the data layer
- [Android ViewModel](https://developer.android.com/topic/libraries/architecture/viewmodel) — used by `TaskViewModel`
- [Android AppWidgetProvider](https://developer.android.com/reference/android/appwidget/AppWidgetProvider) — used by `TaskWidgetProvider`
- [CLAUDE.md](../../../../../../../../CLAUDE.md) — project-wide conventions, glossary, architecture decisions
