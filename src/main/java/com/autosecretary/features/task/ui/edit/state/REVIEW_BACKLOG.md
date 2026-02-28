# Review Backlog — task/ui/edit/state

## Status: All onboarding issues fixed ✓

All friction points have been addressed with targeted documentation improvements:

### ✓ FIXED: TaskEditState — preserved fields now explained

**File:** `TaskEditState.java:17-21`
**Fix:** Expanded docstring to explain that `periodCompletions`, `periodStart`, and `carryoverDebt` hold scheduler state and must be preserved to avoid resetting progress counters. Added link to `TaskCore.History`.

### ✓ FIXED: TaskEditState — field groups now documented

**File:** `TaskEditState.java:31-80`
**Fixes:**
- Scheduling & priority group: clarified fixed-date field status (not UI-exposed) with link to CLAUDE.md
- Added inline comments to min/max duration and cooldown explaining units (minutes/days)
- Added comment for adaptive field explaining auto-adjustment behavior
- Repetition group: clarified user-editable fields vs scheduler-managed state with inline explanations
- Progress tracking group: clarified optional nature and semantic of each field with units/context

### ✓ FIXED: TaskEditState — budget integration explained

**File:** `TaskEditState.java:45-48`
**Fix:** Added comment explaining optional budget integration feature and link to CLAUDE.md for details.

### ✓ FIXED: PrefSlotEditState — domain concept now explained

**File:** `PrefSlotEditState.java:8-13`
**Fix:** Expanded docstring to explain "preferred slot" concept (day-of-week + start-time pattern) with link to CLAUDE.md glossary.

### ✓ FIXED: PrefSlotEditState — days field semantics clarified

**File:** `PrefSlotEditState.java:11-12`
**Fix:** Added inline comment explaining that days are preferred days of week.

### ✓ FIXED: TaskEditDefaults — constants now documented

**File:** `TaskEditDefaults.java:29-44`
**Fixes:**
- Scheduling constraints: Added units (minutes for duration, days for cooldown) and context
- Repetition constants: Clarified that REPS=1 means "1 repetition" and PERIOD_UNIT defaults to DAY
- Progress tracking: Explained optional nature (unit="" means no tracking) and clarified semantics of each field

---

## Deferred Observations (architectural, not onboarding)

### [nit] TaskEditState — field grouping as nested POJOs

**File:** `TaskEditState.java:50-66`

**Observation:** Repetition-related, budget, and progress fields form conceptual groups that could be nested POJOs for clarity.

**Why deferred:** Extracting would complicate form binding. Current flat structure with comment grouping is pragmatic for a UI form POJO.

---

### Related Issue: Field synchronization complexity (task/ui/edit level)

The parent backlog at `task/ui/edit/REVIEW_BACKLOG.md` documents "Four parallel field walks across FormInput → TaskEditState → TaskEditStateMapper". This is a higher-level concern; no state-package-level action needed.
