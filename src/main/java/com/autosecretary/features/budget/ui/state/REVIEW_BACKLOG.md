# Review Backlog — budget/ui/state

## Open Issues

### [consider] `TimeRangeFilter.DAYS_30` carries `months = 0` which callers never read
**File:** `TimeRangeFilter.java:5, 10`

`BudgetOverviewLoader` checks `if (resolvedFilter == TimeRangeFilter.DAYS_30)` by identity and never reads `.months` in that branch — the `0` value is purely nominal. This means the `months` field value for `DAYS_30` carries no semantic weight and is slightly misleading (0 months ≠ 30 days). The javadoc comment mitigates this. No code change needed unless the design is revised to treat `months` as the sole dispatch value.

---

### [keep] `UiText` — exemplary javadoc and design documentation
**File:** `UiText.java:7-11`

Class javadoc explains the pattern clearly: "Deferred string wrapper used in ViewModel LiveData. Resolves a string resource (or literal) only when a Context is available, keeping the ViewModel free of Android Context dependencies." This is excellent onboarding documentation that explains both the what (deferred string) and the why (Context decoupling for MVVM). Methods are self-explanatory with this context. No changes needed.
