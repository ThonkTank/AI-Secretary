# Review Backlog — meal/

## Open Issues

[consider] `ui/package-info.java` — The `ui/` folder contains only an empty package declaration stub; no meal UI has been built yet (confirmed by CLAUDE.md: "no meal UI exists yet"). A reader scanning the tree will click into this folder expecting code and find nothing. Keeping it signals intent about where future UI code should live, which has value. Removing it simplifies the tree but erases the placeholder. Defer until a meal UI is actively planned — at that point the stub would immediately be joined by real files.

## Acknowledged Good Patterns

[keep] Top-level `application/`, `data/`, `domain/`, `ui/` layer split — consistent with the rest of the codebase (budget, task). A reader moving between features sees identical layer boundaries, which makes the entire project predictable.

[keep] `data/` top level is empty of public surface area — all implementation is under `data/internal/`. This correctly signals that the data layer's internals are not part of the meal feature's public contract; callers interact only with `domain/` repository interfaces.

[keep] `domain/internal/` for `HouseholdEnergyService` and `MealAmountFormat` — both are correctly hidden from the domain's public surface. `HouseholdEnergyService` does TDEE/BMR arithmetic consumed by `WeeklyFoodTargetService`; `MealAmountFormat` is a formatting helper shared by `PantryItem` and `ShoppingListItem`. Neither needs to be visible outside the domain package.
