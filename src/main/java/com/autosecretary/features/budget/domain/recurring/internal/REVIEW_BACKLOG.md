# Review Backlog — recurring/internal/

## Open Issues

None at this time.

## Deferred (no changes required)

- [nit] `PayeeGrouper.java:96-115` — `levenshteinDistance()` allocates a 2D array on every call. For large payee strings this is wasteful. Low priority: payee strings are typically short, but could add a length check early return for empty strings. Already does so at callsite (line 46) but worth making explicit in the method. (Deferred: optimization is low-impact for typical payee string lengths)

## Earlier Deferred

- [consider] `DatePatternDetector.java:181` — `PatternResult.value` is a dual-purpose field (day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`) and `dayOfWeek` is null except for `WEEKLY`. A sealed interface hierarchy would make each variant self-describing and eliminate the silent nullability convention. However, that would add 4 new files and a visitor/instanceof dispatch pattern for what is ultimately a 4-variant union — more moving parts for a contained internal type. Current form is well-documented via Javadoc. (Deferred)
