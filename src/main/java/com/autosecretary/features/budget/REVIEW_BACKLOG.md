# Review Backlog — features/budget (top-level)

Cross-cutting issues that span multiple sub-packages.

## Open Issues

- [consider] **`BudgetRecurringTemplateEntity.recurringValue` is a dual-purpose field spanning data/domain boundary** — mirrors the internal `PatternResult.value` dual-purpose (promoted from `domain/internal/REVIEW_BACKLOG.md`). The entity stores `recurringValue` with different semantics depending on `recurringType`: day-of-month for `MONTHLY_DAY`, interval days for `INTERVAL`, always 0 for `MONTHLY_LAST`/`WEEKLY`. This is a silent convention that crosses the data/domain boundary. A proper fix would require a schema migration or a sealed hierarchy which is out of scope. Deferred.

- [violation] **`BudgetRepository.java` domain interface imports `data.entity` types** — `BudgetRepository` (in `domain/`) imports `BudgetAccount`, `BudgetCategory`, `BudgetLimit`, and `BudgetTransactionEntity` from `data.entity`. The interface provides dependency inversion in form (interface in domain) but not substance (method signatures use data-layer types). Fixing requires introducing domain-level types for account/category/transaction/limit and mapping in the repository implementation. Deferred — large cross-cutting refactor affecting every BudgetRepository caller.
