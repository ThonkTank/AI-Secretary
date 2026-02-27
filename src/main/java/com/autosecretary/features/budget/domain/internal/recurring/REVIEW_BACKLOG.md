# Review Backlog — recurring/

## Open Issues

- [consider] `DatePatternDetector.java:181` — `PatternResult.value` is a dual-purpose field (day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`) and `dayOfWeek` is null except for `WEEKLY`. A sealed interface hierarchy would make each variant self-describing and eliminate the silent nullability convention. However, that would add 4 new files and a visitor/instanceof dispatch pattern for what is ultimately a 4-variant union — more moving parts for a contained internal type. Current form is well-documented via Javadoc. (Demoted from domain/internal/REVIEW_BACKLOG.md; deferred)

- [keep] `PayeeGrouper.java:83–102` — `levenshteinDistance` allocates a full O(n×m) DP matrix. A two-row rolling array would halve memory use but makes the code meaningfully harder to read. Current form is correct and clear; the optimization is not justified unless profiling confirms a hot-path bottleneck. (Deferred)
