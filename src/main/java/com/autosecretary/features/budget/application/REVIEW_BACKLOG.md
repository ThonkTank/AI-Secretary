# Review Backlog — budget/application

## Open Issues

*(none)*

---

## Acknowledged Good Patterns

### [keep] `importing/` sub-package for all import-related code
**Path:** `application/importing/`

The import sub-package cleanly groups all import-related orchestration (use cases,
transaction mapping, file parsing) while keeping unrelated workflow use cases at the
`application/` root. A reader scanning for import behaviour knows exactly where to look.

### [keep] Flat use-case surface at `application/` root
**Path:** `application/CalculateEffectiveBudgetLimitUseCase.java`,
`application/CreateTransferUseCase.java`,
`application/LoadBudgetWidgetSummaryUseCase.java`,
`application/BudgetSeedService.java`

Four files at the root — a reader can take in the complete public orchestration surface
without drilling into sub-packages. Adding a new workflow use case here is straightforward.
