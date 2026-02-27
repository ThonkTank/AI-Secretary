# Review Backlog — task/ui/edit/internal/editor

## Open Issues

### [warning] TaskEditSectionBinder.java:293-325 — SchedulingViews 15-parameter constructor

**File:** `TaskEditSectionBinder.java:293-325`

**Concern:** `SchedulingViews` constructor takes 15 parameters, 11 of which are `EditText`. All same-typed positional args — swapping any two produces a silent bug. Budget fields (`budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`) are conceptually separate from scheduling/timing fields.

**Suggested alternative:** Split into two view groups or use a builder. Deferred — too large to fix in isolation without risking regressions in `TaskEditFormInputReader` and `TaskEditFormViews`.

---

### [nit] TaskEditFormViews.java — thin re-aggregation adapter

**File:** `TaskEditFormViews.java`

**Concern:** Takes four `*Views` structs from `TaskEditSectionBinder` and flattens specific fields into a new type passed to `TaskEditFormValidator`. Adds a file and class for minimal gain. Remove only if the validator is refactored to accept fields directly.

---

### [nit] TaskEditFormValidator.java:111-113 — `validateMinMaxPair` passthrough

**File:** `TaskEditFormValidator.java:111-113`

**Concern:** `validateMinMaxPair` is a pure one-line passthrough to `validateFirstNotAboveSecond` with no argument transformation. Inline at the two call sites for directness, or keep as a named wrapper for domain clarity. Deferred — the readability tradeoff is close; keeping it as a named wrapper is defensible.

---

### [nit] PrefSlotSectionController.java:52-57,74-79 — duplicate repetition-field reading

**File:** `PrefSlotSectionController.java:52-57,74-79`

**Concern:** The same four-arg repetition-field-reading block appears verbatim in both `rebuildPrefSlotUI()` and `onRepetitionChanged()`, but the two call sites pass those args to different presenter methods (`computeCurrentRepsPerDay` vs `onRepetitionChanged`). Without introducing a data carrier or restructuring the presenter API, the duplication cannot be eliminated cheaply. Deferred — two call sites, readable enough in context.

---

### [nit] GoalSectionController.java:96-98, PrefSlotSectionController.java:181-183 — duplicate `dimenPx` helper

**Files:** `GoalSectionController.java:96-98`, `PrefSlotSectionController.java:181-183`

**Concern:** Identical `dimenPx(@DimenRes int)` one-liner helper duplicated in both controllers. Extracting to a shared utility would add a file for negligible gain. Worth revisiting if a third controller appears in this package.

