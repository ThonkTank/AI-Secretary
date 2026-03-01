# Task application layer

The `application/` layer orchestrates task feature workflows. It sits between the UI
and the domain/data layers: use cases call domain services and DAOs, then notify the
UI via callbacks. All DB work runs on the shared `ExecutorService`; callbacks are
dispatched to the main thread via `callbackDispatcher` (a `Handler`-backed `Executor`).

> **Key terminology:** Domain terms used throughout this layer (Task, Slot, PrefSlot, Period,
> Streak, Adaptive, etc.) are defined in the project-root
> [`CLAUDE.md` Glossary](../../../../../../CLAUDE.md#glossary). Read that first if any term
> is unfamiliar.

## Entry points at a glance

| Class | Role |
|---|---|
| `TaskDataService` | Async DAO wrapper: load all tasks, load one task, save a task, start/stop manual timer, delete a task. The default data-access entry point from the UI. |
| `RegenerateScheduleUseCase` | Reads all tasks, runs `TaskSlotGenerator` over 7 days (today + 6), writes generated slots back. Called by `DailyPlanningReceiver` at midnight and on app start. |
| `CheckOffTaskUseCase` | Drives the two-phase check-off (first tap → STARTED, second tap → COMPLETED) via `TaskSlotToggleMutation`. Also triggers budget expense booking and meal integration. |
| `AdjustTaskProgressUseCase` | Increments or decrements a task's progress counter (used for tasks with a progress target). Routes completions through `TaskLifecycleManager` so streaks are updated consistently with `CheckOffTaskUseCase`. |

## Sub-packages

| Package | README | Role |
|---------|--------|------|
| `calendar/` | [`calendar/`](calendar/README.md) | `TaskCalendarService` contract and `ScheduleWindow` DTO |
| `config/` | [`config/`](config/README.md) | Per-day scheduling-window configuration (`TaskScheduleConfigRepository`) |
| `listmodel/` | [`listmodel/`](listmodel/README.md) | Flat presentation DTOs: `TaskListItem` and `TaskListItemMapper` |
| `internal/` | [`internal/`](internal/README.md) | Android implementations: alarms, calendar reader, budget integration, atomic mutations |

## Threading contract

All DB access runs on the `ExecutorService` injected via `AppCompositionRoot`.
Callbacks are dispatched on the `callbackDispatcher` (main thread). **Never call
a use case or DAO method from the main thread directly.**

## Reading order for newcomers

1. **`TaskDataService`** — simplest class; shows the async callback pattern used everywhere.
2. **`RegenerateScheduleUseCase`** — core scheduling pipeline entry point; read alongside `domain/README.md`.
3. **`CheckOffTaskUseCase`** — shows how a two-phase check-off chains through mutation + side effects.
4. **[`listmodel/README.md`](listmodel/README.md)** — understand `TaskListItem` before reading ViewModel or adapter code.
5. **[`internal/README.md`](internal/README.md)** — read last; covers alarm wiring, calendar integration, atomic mutations.

## Placement rules for new code

- **Entry use-cases at the root** — top-level workflows with a single public `execute()` method.
- **`calendar/`** — calendar service contracts and DTOs; Android implementations go in `internal/calendar/`.
- **`config/`** — scheduling-window config repository.
- **`listmodel/`** — list presentation DTOs and their mapper. Do not introduce `model/`.
- **`internal/`** — Android/infrastructure implementations; `internal/mutations/` for multi-DAO atomic writes.
