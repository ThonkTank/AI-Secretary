# Review Backlog — task/domain/scheduling

## Open Issues

### [consider] BudgetEligibility.availableCents is documented as used by scheduling but never read
**File:** `TaskBudgetEligibilityService.java:49–56`

`BudgetEligibility` is a two-field record — `enoughBudget` (boolean) and `availableCents` (long). The Javadoc says `availableCents` is "Used by scheduling algorithms to rank tasks by affordability." In practice, `TaskScorer` only reads `.enoughBudget()` and stores it as a plain `boolean` in `TaskScoringSnapshot`. The `availableCents` value is computed in `TaskBudgetEligibilityFromBudgetLookup` but never consumed by any scheduling or scoring code.

**Simpler alternative:** Either (a) remove `availableCents` from `BudgetEligibility` and return only a `boolean` from `eligibilityFor`, or (b) if affordability ranking is planned for the future, keep the field but remove the misleading "Used by scheduling algorithms" Javadoc claim.

**Why safe:** No scheduling or scoring code reads `availableCents` today. Removing it simplifies the interface. Requires verifying `TaskBudgetEligibilityFromBudgetLookup` does not use `availableCents` for anything else before removing.

---

