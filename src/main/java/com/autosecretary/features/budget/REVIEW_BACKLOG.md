# Review Backlog — features/budget (top-level)

Cross-cutting issues that span multiple sub-packages. Sub-directory backlogs contain their own local issues.

## Open Issues

- [warning] **`TransactionKind` degrades to String in query projections** — `MonthlyOverviewItem` gets `transactionKind` as an enum field; confirm a Room type converter exists for `TransactionKind`, or add one. `data/dao/TransactionDao.java`, `ui/internal/BudgetOverviewLoader.java:143`
