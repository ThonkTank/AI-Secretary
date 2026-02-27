# Review Backlog — budget/domain

## Open Issues

- [warning] `BudgetImportRepository.java:86` — `notifyBudgetDataUpdated()` is a UI lifecycle operation on a domain interface; its own Javadoc flags it; fix: move to a separate `BudgetDataNotifier` interface owned by the application layer, or trigger from the use case.
- [warning] `MonthlyOverviewItem.java` — mutable public-field POJO inconsistent with the rest of the domain layer. Room's field-injection requirement means conversion to a record requires splitting into two distinct result types (one per query, since the two queries return different column sets). Safe fix: split into `MonthlyTransactionItem` (full view) and a simpler projection type.

