# meal/application

Application layer for the meal-planning feature. Sits between the UI and the domain: it
orchestrates domain services and repositories, enforces input validation, and keeps controllers
thin.

## Layer responsibilities

- Coordinate domain services (RecipeScalingService, ShelfLifeService, etc.) across repositories
- Translate raw UI input (IDs, raw amounts, strings) into validated domain objects
- Provide the UI with pre-sorted, ready-to-render data (`LoadMealHomeUseCase`)
- Provide meal-planner application operations for the UI layer

This layer contains **no persistence logic** (all DB access is in `meal.data`) and
**no UI logic** (layouts, views, adapters). It may freely call domain services and repositories.

## Entry points

| Class | Role |
|---|---|
| `MealPlannerDataService` | Async UI-facing facade. Owns executor dispatching and delegates to focused application classes. |
| `LoadMealHomeUseCase` | Builds the home payload: plans, recipes, pantry, shopping, and summary aggregates. |
| `LoadMealWeeklyProgressUseCase` | Builds weekly calorie and food-group progress. |
| `MealPlanMutationUseCase` | Plans recipes, toggles completion, and deletes meal plans. |
| `MealShoppingUseCase` | Updates shopping items and creates shopping/pantry entries. |
| `MealManagementDataService` | Management lists and CRUD for recipes, ingredients, pantry, household members, and cooking preferences. |

## Reading order for newcomers

1. **`MealPlannerDataService`** — start here to see the UI-facing callback contract.
2. **Focused application classes** — read the class for the flow you are changing.
3. **Domain services** — read the domain README for the stateless helpers used by this layer.

## Key concepts

**periodKey** — A shopping-list and weekly-target grouping key, stored as an ISO-8601 date
string (`LocalDate.toString()`, e.g. `"2024-12-30"`). It identifies which weekly period an item
belongs to. Always use `LocalDate.now().toString()` to produce the current period key.

**DGE targets** — The German Nutrition Society (Deutsche Gesellschaft für Ernährung, DGE)
publishes reference values for weekly food group intake. See `WeeklyFoodTargetService` in the
domain layer for the calculation formulas.

**Pantry depletion** — When a meal task is completed, the task-owned
`TaskMealCompletionService` adapter reduces the
pantry stock for each ingredient used in the recipe, consuming older items first (sorted by
expiry date). Items depleted below `DEPLETION_EPSILON` are deleted rather than saved at near-zero.
