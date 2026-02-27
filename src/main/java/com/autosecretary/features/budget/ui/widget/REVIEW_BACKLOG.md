# Budget Widget – Review Backlog

## Open Issues

[nit] BudgetWidgetProvider:66–78 (buildPendingIntent method) — Null parameter `budgetAction` creates unnecessary branching (line 70). Could be simplified by using method overloading or eliminating the null check (e.g., always setting the extra, using defaulting, or changing the API). However, the current approach is reasonable defensive programming and is not a priority for simplification.

## Completed Fixes

- **[simplify] Removed pass-through repository abstraction**: Deleted unused `BudgetWidgetRepository` interface and `BudgetWidgetRoomRepository` implementation. Refactored `LoadBudgetWidgetSummaryUseCase` to directly depend on `TransactionDao` and `BudgetLimitDao` instead of the unnecessary intermediate repository layer. Reduced LOC and eliminated indirection without changing behavior.
- **[simplify] Centralized widget dependency creation**: Added `createLoadBudgetWidgetSummaryUseCase()` factory method to `AppCompositionRoot` and updated `BudgetWidgetProvider` to use it instead of manually instantiating `AppDatabase`, repository, and usecase. Follows project's existing DI pattern and avoids direct `AppDatabase.getInstance()` calls in the widget provider.
