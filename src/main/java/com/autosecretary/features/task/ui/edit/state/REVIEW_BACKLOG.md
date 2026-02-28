# Review Backlog — task/ui/edit/state

## Current Issues

None.

---

## Resolved Issues (this run)

### ✓ FIXED: Unnecessary null-checks in GoalSectionController

**File:** `../../internal/editor/GoalSectionController.java:47-48`

**Fix:** Removed defensive null-checks on `goalIcon` and `goalColorHex` fields. TaskEditState initializes these fields to non-null defaults, and TaskEditStateMapper.fromTask() guarantees non-null values via Objects.requireNonNullElse(). The null-checks were redundant defensive programming. Added clarifying comment explaining why the fields are always non-null.

---

### ✓ FIXED: TaskEditPresenter.resetProgress() sets unit to null instead of empty string

**File:** `TaskEditPresenter.java:187`

**Fix:** Changed `editState.unit = null;` to `editState.unit = "";` with clarifying comment. This aligns with the established convention that empty string means "no progress tracking" (as documented in TaskEditDefaults.UNIT and TaskEditState:64), eliminating the need for defensive null-checks in callers.

---

## Previous Issues (all resolved ✓)

### ✓ FIXED: TaskEditState — preserved fields now explained (prior run)
**File:** `TaskEditState.java:17-21`

### ✓ FIXED: TaskEditState — field groups now documented (prior run)
**File:** `TaskEditState.java:31-80`

### ✓ FIXED: TaskEditState — budget integration explained (prior run)
**File:** `TaskEditState.java:45-48`

### ✓ FIXED: PrefSlotEditState — domain concept now explained (prior run)
**File:** `PrefSlotEditState.java:8-13`

### ✓ FIXED: PrefSlotEditState — days field semantics clarified (prior run)
**File:** `PrefSlotEditState.java:11-12`

### ✓ FIXED: TaskEditDefaults — constants now documented (prior run)
**File:** `TaskEditDefaults.java:29-44`

### ✓ FIXED: TaskEditState — confusing field grouping in repetition section (prior run)
**File:** `TaskEditState.java:53-61`

---

## Deferred Observations (architectural, not state-package scope)

### [nit] TaskEditState — field grouping as nested POJOs

**File:** `TaskEditState.java:50-66`

**Observation:** Repetition-related, budget, and progress fields form conceptual groups that could be nested POJOs for clarity.

**Why deferred:** Extracting would complicate form binding. Current flat structure with comment grouping is pragmatic for a UI form POJO.

---

### Related Issue: Field synchronization complexity (task/ui/edit level)

The parent backlog at `task/ui/edit/REVIEW_BACKLOG.md` documents "Four parallel field walks across FormInput → TaskEditState → TaskEditStateMapper". This is a higher-level concern; no state-package-level action needed.
