# Review Backlog — meal/application

## Resolved Issues (Completed in this run)

✅ **[friction]** No README.md in `meal/application` — Added `README.md` with layer overview, entry-point table, reading order, and key concept definitions (periodKey, DGE targets, package rounding, pantry depletion).

✅ **[comment]** `package-info.java` empty — Added package-level Javadoc with a one-line purpose description and link to README.

✅ **[friction]** `MealPlannerPresenter` — no class-level Javadoc. Added Javadoc explaining role, invocation context, and relationship to domain/data layers.

✅ **[comment]** `MealPlannerPresenter.getWeekMealPlans()` — asymmetric -3/+10 window unexplained. Added Javadoc + inline comment explaining the intentional window.

✅ **[comment]** `MealPlannerPresenter.getRecipes()` — silent demo recipe insertion on empty state was an undocumented side effect. Added Javadoc and inline comment.

✅ **[comment]** `MealPlannerPresenter.getShoppingListItemsForToday()` — periodKey format (ISO-8601 date string) undocumented. Added Javadoc.

✅ **[friction]** `TaskMealIntegrationService` — class-level Javadoc missing invocation context and cascade flow. Added full Javadoc with invocation context and numbered cascade steps.

✅ **[comment]** `TaskMealIntegrationService.DEFAULT_MEMBER_ID = 0L` — meaning unclear. Replaced bare constant with an explanatory Javadoc comment.

✅ **[comment]** `TaskMealIntegrationService.DEPLETION_EPSILON` — purpose of threshold unexplained. Added Javadoc.

✅ **[friction]** `ScaleRecipeUseCase` — German one-liner Javadoc, "Precision-Regeln" unexplained. Replaced with English Javadoc explaining scaling precision behaviour.

✅ **[friction]** `RecalculateWeeklyFoodTargetUseCase` — "DGE" undefined abbreviation. Replaced German Javadoc with full English Javadoc explaining DGE, both parameters, and when to call this use case.

✅ **[friction]** `CreatePantryItemUseCase` — German one-liner Javadoc with no detail. Added English Javadoc explaining expiry-date derivation, null purchaseDate fallback, and exception behaviour.

✅ **[friction]** `CreateShoppingListItemUseCase` — "Packungsrundung/Ueberschuss" opaque jargon. Added English Javadoc with a concrete package-rounding example and periodKey explanation.

## Open Issues

### Deferred (architectural — require decisions outside this scope)

**[consider]** `TaskMealIntegrationService.java:28,128` — `DEFAULT_MEMBER_ID = 0L` and `itemId = 0L` are passed to `ConsumptionLog.Builder` for task-triggered meal completions. Any member-filtered query over `ConsumptionLog` will silently exclude these entries (memberId=0 matches no real member). Acceptable short-term placeholder, but a design decision is needed on whether task-driven consumption should contribute to per-member nutrition tracking or remain in an "unassigned" bucket. Deferred — requires a broader product decision.

**[coupling]** `TaskMealIntegrationService.java:3-4` — imports `task.data.Task` and `task.data.TaskPlannedMeal` directly from a different feature's data layer. The dependency arrow is `meal.application → task.data`, which crosses both a feature boundary and a layer boundary. Fix: define a `TaskMealDelegate` interface in `meal.domain` (or `task.application`) with the relevant fields, and have the task feature provide the implementation. Deferred — requires coordinated change across two features.
