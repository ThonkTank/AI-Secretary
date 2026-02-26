# Budget Widget – Review Backlog

## Open Issues

[nit] BudgetWidgetProvider:41–47 — Direct `AppDatabase.getInstance()` + inline constructor bypasses the composition root; expose a factory method on `AppCompositionRoot` instead of wiring dependencies manually inside the provider.
