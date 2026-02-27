# Review Backlog — budget/application

## Open Issues

### [nit] BudgetSeedService.java:55–84 — String-literal category names create silent coupling
Demo data uses string-literal category names ("Gehalt", "Miete") for lookup that must
match insertions at lines 55–61 — silent coupling with no compile-time check.
Refactor to use the inserted entity references directly or a lookup map keyed by the
same constants.

