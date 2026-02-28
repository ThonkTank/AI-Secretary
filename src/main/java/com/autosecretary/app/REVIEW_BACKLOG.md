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

---

### ✅ [nit] `SettingsController.showSettingsMenu` — hardcoded array size coupled to constant count
**Path:** `app/settings/SettingsController.java:141`

**Fixed:** Replaced `new String[4]` + four explicit `options[OPTION_*] = ...` assignments with a
single inline array initializer in declaration order. The array now self-sizes to its elements;
adding a future option requires only one line and cannot silently undersize the array.

---

### ✅ [nit] Inconsistent null guard between the two intent-inspection helpers in `MainActivity`
**Path:** `app/MainActivity.java`

Removed unreachable `if (intent == null) return false` guard from `shouldOpenTaskCreateFromIntent`.
Both sibling methods are only called with a non-null intent from `navigateToIntentTarget()`; the
guard was dead code that falsely implied null could occur. Methods are now symmetric.

---
