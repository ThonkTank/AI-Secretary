# Task application package map

Use the `application/` layer to orchestrate task feature workflows and expose app-facing contracts.

- **Entry use-cases (`application/`)**
  - Keep top-level use-cases and orchestration actions that are direct entry points from UI flows.
  - Examples: progress adjustments, check-off/delete, schedule regeneration, and task data reads/writes via `TaskDataService`.

- **Calendar contracts (`application/calendar/`)**
  - Keep calendar-facing service contracts and DTOs used by scheduling/list projections.
  - Examples: `TaskCalendarService`, `ScheduleWindow`.
  - Note: `TaskCalendarEvent` lives in `domain/` (domain scheduling code uses it directly).

- **Schedule config (`application/config/`)**
  - Keep schedule configuration repository used by config UI and scheduling window lookup.
  - Example: `TaskScheduleConfigRepository`.

- **List presentation models (`application/listmodel/`)**
  - Keep immutable list rendering DTOs and list-only mapping helpers.
  - Examples: `TaskListItem`, `TaskListItemMapper`.

- **Internal implementations (`application/internal/`)**
  - Keep Android/infrastructure implementations of application contracts under explicit internal subpackages.
  - Example: `internal/calendar/CalendarReader` as the Android-backed implementation of `TaskCalendarService`.
  - Keep low-level mutation helpers under `internal/mutations/` (for example `TaskSlotToggleMutation`).

Do not introduce `application/model` for task list display objects; use `application/listmodel` consistently.
