# Review Backlog — task/ui/edit/internal/editor

## Open Issues

[simplify] TaskEditSectionBinder.java:290-323 — `SchedulingViews` constructor takes 15 parameters, 11 of which are `EditText`. All same-typed positional args — swapping any two produces a silent bug. Budget fields (`budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`) are conceptually separate from scheduling/timing fields. Split into two view groups or use a builder. Deferred — too large to fix in isolation without risking regressions in `TaskEditFormInputReader` and `TaskEditFormViews`.

[consider] TaskEditFormViews.java — Thin re-aggregation adapter: takes four `*Views` structs from `TaskEditSectionBinder` and flattens specific fields into a new type passed to `TaskEditFormValidator`. Adds a file and class for minimal gain. Remove only if the validator is refactored to accept fields directly.

