# Budget UI Internal – Review Backlog

## Open Issues

[nit] BudgetOverviewLoader:~143–154 — Hardcoded German strings "Überweisung", "Überweisung · ", "Buchung" not in string resources; will be missed during localization.

[nit] BudgetOverviewLoader.java:158 — Magic number `29` in `now.minusDays(29)` (inclusive 30-day window). Correct but reads like an off-by-one; a named constant like `DAYS_30_WINDOW_OFFSET = 29` would prevent a future "fix".
