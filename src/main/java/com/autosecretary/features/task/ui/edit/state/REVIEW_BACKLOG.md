# Review Backlog — task/ui/edit/state

## Status
**Clean** — no active issues requiring immediate fix. The state package consists of straightforward POJOs and constants with appropriate documentation and design patterns.

## Deferred Observations (for future consideration)

### [nit] TaskEditState — field grouping as nested POJOs

**File:** `TaskEditState.java:50-66`

**Observation:** Repetition-related fields (`reps`, `perPeriod`, `periodUnit`, `periodCompletions`, `periodStart`, `completeFirst`, `carryoverDebt`) form a conceptual group that mirrors `TaskCore.Repetition`. Similarly, budget fields (`budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`) and progress fields (`unit`, `target`, `current`, `resetPerRep`, `minPerRep`, `maxPerRep`) are grouped.

Currently managed via comment sections. Could be extracted into nested POJOs for clarity and to parallel `TaskCore`'s structure.

**Why no immediate fix:** Extracting would complicate form binding (FormInput readers and FormViews would need to navigate nested fields). Current flat structure with comment grouping is pragmatic for a UI form POJO. Revisit only if form binding infrastructure improves to handle nesting naturally.

---

### [nit] TaskEditDefaults — undocumented delegation to TaskCore constants

**File:** `TaskEditDefaults.java:24-25`

**Observation:** `GOAL_ICON` and `GOAL_COLOR_HEX` delegate to `TaskCore.DEFAULT_GOAL_ICON` and `TaskCore.DEFAULT_GOAL_COLOR_HEX` without comment. A reader of `TaskEditDefaults` might not realize these are sourced from the domain model, not defined locally.

**Why deferred:** Delegation is intentional and correct (UI defaults should defer to domain defaults). Low-risk change: add a comment or keep as-is. Not blocking anything.

---

### Related Issue: Field synchronization complexity (task/ui/edit level)

The parent backlog at `task/ui/edit/REVIEW_BACKLOG.md` documents "Four parallel field walks across FormInput → TaskEditState → TaskEditStateMapper". This affects `TaskEditState` but is a higher-level concern spanning FormInput, applyForm(), and both mapper directions. Already documented above; no state-package-level action needed.
