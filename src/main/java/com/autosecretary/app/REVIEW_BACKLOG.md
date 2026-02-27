# Review Backlog — `app/`

## Open Issues

---

### [consider] `Preferences.java` lives in `app/` but only serves task scheduling
**Path:** `app/Preferences.java`

**Why it's hard to navigate today:**
A developer reading `features/task/ui/list/TaskViewModel` sees it import `com.autosecretary.app.Preferences`
and must know to look in the app-level package for a scheduling-window config class that has nothing
to do with the app's lifecycle or wiring. `Preferences` reads day start/end times per `DayOfWeek` from
SharedPrefs — a pure task-scheduling configuration concern.

**Proposed change:**
Move to `features/task/application/internal/` (the home for Android/infrastructure implementations of
task-domain contracts). Rename to `SchedulingWindowPreferences` or `DayWindowPreferences` to make the
purpose concrete. Update imports in `TaskViewModel`, `TaskViewModelFactory`, and `AppCompositionRoot`.

**Why it reduces mental load:**
The class would live next to the rest of the task scheduling infrastructure. Readers of the task feature
wouldn't need to jump to `app/` for one small class.

**Tradeoffs / risks:**
The move is low-risk (3 files touched, no logic change). However, `app/` is a conventional home for
SharedPreferences wrappers in Android projects, so the current placement isn't wrong — just less
discoverable for task-feature work. Tradeoffs are close; defer until a related task touches these files.
