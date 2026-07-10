# meal/application

Application layer for the meal-planning feature. Sits between the UI and the domain: it
orchestrates domain services and repositories, enforces input validation, and keeps controllers
thin.

## Layer responsibilities

- Coordinate domain services (RecipeScalingService, ShelfLifeService, etc.) across repositories
- Translate raw UI input (IDs, raw amounts, strings) into validated domain objects
- Provide the UI with pre-sorted, ready-to-render data (MealPlannerPresenter)
- Provide meal-planner application operations for the UI layer

This layer contains **no persistence logic** (all DB access is in `meal.data`) and
**no UI logic** (layouts, views, adapters). It may freely call domain services and repositories.

## Entry points

| Class | Role |
|---|---|
| `MealPlannerPresenter` | Thin coordinator called by `MealPlannerFragment`; feeds sorted data to the UI and handles basic user actions (plan recipe, toggle completed, add item). |

## Reading order for newcomers

1. **`MealPlannerPresenter`** — best first read; shows how the UI interacts with all three
   repositories and what data-shaping happens at this layer.
2. **Domain services** — read the domain README for the stateless helpers used by this layer.

## Key concepts

**periodKey** — A shopping-list and weekly-target grouping key, stored as an ISO-8601 date
string (`LocalDate.toString()`, e.g. `"2024-12-30"`). It identifies which weekly period an item
belongs to. Always use `LocalDate.now().toString()` to produce the current period key.

**DGE targets** — The German Nutrition Society (Deutsche Gesellschaft für Ernährung, DGE)
publishes reference values for weekly food group intake. See `WeeklyFoodTargetService` in the
domain layer for the calculation formulas.

**Package rounding** — When adding a shopping-list item, `ShoppingPackagingService` rounds the
required amount up to the nearest standard package size (e.g., 450 g → 1× 500 g bag) and
records any leftover as `excessAmount`.

**Pantry depletion** — When a meal task is completed, the task-owned
`TaskMealCompletionService` adapter reduces the
pantry stock for each ingredient used in the recipe, consuming older items first (sorted by
expiry date). Items depleted below `DEPLETION_EPSILON` are deleted rather than saved at near-zero.
