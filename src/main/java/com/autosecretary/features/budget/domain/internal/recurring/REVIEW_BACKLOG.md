# Review Backlog — recurring/

## Open Issues

- [nit] `levenshteinDistance` allocates a full O(n×m) DP matrix per comparison; optimize to two-row rolling arrays if bulk imports become a concern. `PayeeGrouper.java:89–107`
