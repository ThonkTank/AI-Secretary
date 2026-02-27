# Review Backlog — recurring/

## Open Issues

- [consider] `DatePatternDetector.java:119,129` — `calculateIntervals(dates)` is called independently in both `checkWeekly` and `checkInterval`. When neither monthly pattern matches, the interval list is built twice. Could be pre-computed in `detectDatePattern` and passed in, but would complicate the package-private method signatures. Minor: only matters on bulk-import workloads. (Deferred)

- [keep] `PayeeGrouper.java:83–102` — `levenshteinDistance` allocates a full O(n×m) DP matrix. A two-row rolling array would halve memory use but makes the code meaningfully harder to read. Current form is correct and clear; the optimization is not justified unless profiling confirms a hot-path bottleneck. (Deferred)

