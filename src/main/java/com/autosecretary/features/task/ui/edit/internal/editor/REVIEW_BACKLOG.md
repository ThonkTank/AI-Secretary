# Review Backlog — task/ui/edit/internal/editor

## Open Issues

### [warning] TaskEditSectionBinder.java:293-325 — SchedulingViews 15-parameter constructor

**File:** `TaskEditSectionBinder.java:293-325`

**Concern:** `SchedulingViews` constructor takes 15 parameters, 11 of which are `EditText`. All same-typed positional args — swapping any two produces a silent bug. Budget fields (`budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`) are conceptually separate from scheduling/timing fields.

**Suggested alternative:** Split into two view groups or use a builder. Deferred — too large to fix in isolation without risking regressions in `TaskEditFormInputReader` and `TaskEditFormViews`.

---

### [nit] TaskEditSectionBinder.java:214-250 — bindProgress() is 36 lines

**File:** `TaskEditSectionBinder.java:214-250`

**Concern:** Just over the 30-line threshold. The method is cohesive and has only one listener, so extraction would produce a trivial helper. Low priority.

---

### [nit] TaskEditFormViews.java — thin re-aggregation adapter

**File:** `TaskEditFormViews.java`

**Concern:** Takes four `*Views` structs from `TaskEditSectionBinder` and flattens specific fields into a new type passed to `TaskEditFormValidator`. Adds a file and class for minimal gain. Remove only if the validator is refactored to accept fields directly.

---

### [nit] PrefSlotSectionController.java:52-57,74-79 — duplicate repetition-field reading

**File:** `PrefSlotSectionController.java:52-57,74-79`

**Concern:** The same four-arg repetition-field-reading block appears verbatim in both `rebuildPrefSlotUI()` and `onRepetitionChanged()`, but the two call sites pass those args to different presenter methods (`computeCurrentRepsPerDay` vs `onRepetitionChanged`). Without introducing a data carrier or restructuring the presenter API, the duplication cannot be eliminated cheaply. Deferred — two call sites, readable enough in context.

---

### [nit] GoalSectionController.java:96-98, PrefSlotSectionController.java:181-183 — duplicate `dimenPx` helper

**Files:** `GoalSectionController.java:96-98`, `PrefSlotSectionController.java:181-183`

**Concern:** Identical `dimenPx(@DimenRes int)` one-liner helper duplicated in both controllers. Extracting to a shared utility would add a file for negligible gain. Worth revisiting if a third controller appears in this package.

---

### [nit] GoalSectionController.java:22-25 — hardcoded GOAL_COLORS palette

**File:** `GoalSectionController.java:22-25`

**Concern:** The 10 colour hex strings are embedded in Java source as a static array. Any palette change requires editing Java code rather than a resource file. The `#AARRGGBB` format is not standard Android color-resource format, so moving them requires a `string-array` in `res/values/arrays.xml` and `Color.parseColor` loading in the controller.

**Suggested fix:** Move to `res/values/arrays.xml` as a `string-array`, load via `getResources().getStringArray(R.array.goal_editor_colors)` in `buildGoalColorGrid`. Deferred — requires adding a new resource; low urgency since palette is stable.
