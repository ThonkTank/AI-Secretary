# Review Backlog — recurring/

## Open Issues

- [warning] `SuggestionScorer.java:54` — `calculateConfidence` takes 5 parameters; three of them (`avgAmount`, `minAmount`, `maxAmount`) are always sourced together from `AmountStats` in `RecurringPatternDetector`. This is a data clump: these three values should be a named value type (e.g. `AmountRange`) so the call site reads `calculateConfidence(count, amountStats, payee)` instead of repeating three fields. **Deferred:** `AmountStats` is a private record inside `RecurringPatternDetector`; extracting a shared type adds a new file in a feature-complete app. Fix if `SuggestionScorer` gains additional callers.


## Resolved (history)

- [✓] Added precondition documentation to `RecurringPatternDetector.AmountStats.from()` — documents that list must be non-empty and throws will occur if empty
- [✓] Added precondition and @throws documentation to `RecurringPatternDetector.analyzePattern()` — clarifies that list must be non-empty with consistent amounts and a detected date pattern
- [✓] Added @throws documentation to `DatePatternDetector.findModeFromCounts()` — clarifies precondition that counts must be non-empty

## Earlier Resolved

All identified onboarding friction has been addressed:
- [✓] Added comprehensive README.md with entry points, data types, and troubleshooting guide
- [✓] Improved RecurringSuggestion javadoc with field explanations and confidence score guidance
- [✓] Translated RecurringBudgetTransaction class-level doc from German to English; added nested enum note
- [✓] Added algorithm overview to RecurringPatternDetector
- [✓] Explained AMOUNT_VARIANCE_THRESHOLD constant with intent and rationale
- [✓] Enhanced PayeeGrouper javadoc with Levenshtein reference and similarity score explanation
- [✓] Added field semantics docs to RecurringScheduleParams (per-type field meaning)
- [✓] Added @param/@return Javadoc to RecurringTemplateScheduler.computeNextDue and computeStatusUpdates
- [✓] Expanded TemplateStatusUpdate Javadoc to explain active=false meaning

## Deferred (no changes required)

- [internal/REVIEW_BACKLOG.md] Pattern design consideration: `DatePatternDetector.PatternResult` dual-purpose `value` field. A sealed interface hierarchy would be more type-safe but adds 4 new files; deferred as current Javadoc is sufficient for the contained scope.
