# Review Backlog — task/ui/edit/internal/editor

## Open Issues

[simplify] TaskEditSectionBinder.java:290-323 — `SchedulingViews` constructor takes 15 parameters, 11 of which are `EditText`. All same-typed positional args — swapping any two produces a silent bug. Budget fields (`budgetRequiredCents`, `budgetAccountId`, `budgetCategoryId`) are conceptually separate from scheduling/timing fields. Split into two view groups or use a builder. Deferred — too large to fix in isolation without risking regressions in `TaskEditFormInputReader` and `TaskEditFormViews`.

[consider] TaskEditFormViews.java — Thin re-aggregation adapter: takes four `*Views` structs from `TaskEditSectionBinder` and flattens specific fields into a new type passed to `TaskEditFormValidator`. Adds a file and class for minimal gain. Remove only if the validator is refactored to accept fields directly.

[consider] TaskEditFormValidator.java:111-113 — `validateMinMaxPair` is a pure one-line passthrough to `validateFirstNotAboveSecond` with no argument transformation.
- Current: `return validateFirstNotAboveSecond(minField, maxField, minMessageResId, maxMessageResId);`
- Option: inline at the two call sites so the full name `validateFirstNotAboveSecond` is visible (though `validateMinMaxPair` is slightly more domain-readable at call sites).
- Deferred — the readability tradeoff is close; keeping it as a named wrapper for domain clarity is defensible.

[consider] TaskEditFormInputReader.java:34-64 — `parseDateSafe`, `parseTimeSafe`, and `parseIntegerNullable` each call `.trim()` twice (once for the null/empty guard, once for the parse call).
- Current: `return value == null || value.trim().isEmpty() ? null : LocalDate.parse(value.trim());`
- Proposed: store `value.trim()` in a local before the ternary.
- Why clearer: eliminates redundant work, though the one-liner is already readable enough for a defer.
- Deferred — gain is marginal; one-liner reads acceptably in context.

[consider] PrefSlotSectionController.java:55-60,77-82 — The same four-arg repetition-field-reading block appears verbatim in both `rebuildPrefSlotUI()` and `onRepetitionChanged()`, but the two call sites pass those args to different presenter methods (`computeCurrentRepsPerDay` vs `onRepetitionChanged`). Without introducing a data carrier or restructuring the presenter API, the duplication cannot be eliminated cheaply. Deferred — two call sites, readable enough in context, fix would require a presenter API change.
