# Review Backlog — recurring/

## Open Issues

None at this time. All identified onboarding friction has been addressed:
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
