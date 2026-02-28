# Review Backlog — meal/

## Open Issues

✅ RESOLVED: `ui/package-info.java` now has proper module-level javadoc. `ui/` contains MealPlannerFragment, and all classes are well-documented. No further action needed.

## Acknowledged Good Patterns

[keep] Top-level `application/`, `data/`, `domain/`, `ui/` layer split — consistent with the rest of the codebase (budget, task). A reader moving between features sees identical layer boundaries, which makes the entire project predictable.

[keep] `data/` top level is empty of public surface area — all implementation is under `data/internal/`. This correctly signals that the data layer's internals are not part of the meal feature's public contract; callers interact only with `domain/` repository interfaces.

[keep] `domain/internal/` for `HouseholdEnergyService` and `MealAmountFormat` — both are correctly hidden from the domain's public surface. `HouseholdEnergyService` does TDEE/BMR arithmetic consumed by `WeeklyFoodTargetService`; `MealAmountFormat` is a formatting helper shared by `PantryItem` and `ShoppingListItem`. Neither needs to be visible outside the domain package.
