# Review Backlog — features/budget (top-level)

Cross-cutting issues that span multiple sub-packages. Sub-directory backlogs contain their own local issues.

## Open Issues

- [warning] **`TransactionKind` degrades to String in query projections** — `MonthlyOverviewItem` gets `transactionKind` as an enum field; confirm a Room type converter exists for `TransactionKind`, or add one. `data/dao/TransactionDao.java`, `ui/internal/BudgetOverviewLoader.java:143`

- [consider] **`BudgetRecurringTemplateEntity.recurringValue` is a dual-purpose field spanning data/domain boundary** — mirrors the internal `PatternResult.value` dual-purpose (promoted from `domain/internal/REVIEW_BACKLOG.md`). The entity stores `recurringValue` with different semantics depending on `recurringType`: day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`. This is a silent convention that crosses the data/domain boundary. A proper fix would require a schema migration or a sealed hierarchy which is out of scope. Deferred.
