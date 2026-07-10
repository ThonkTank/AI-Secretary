# Meal data layer

This package is the data layer for the meal feature. It adapts Room storage to
the domain repository interfaces:

- `MealRepository`
- `PantryRepository`
- `RecipeRepository`

## Package layout

`features/meal/data/` follows the standard data-layer buckets:

- `entity/` owns Room entity classes persisted to the shared database.
- `dao/` owns Room DAO interfaces and query methods.
- `repository/` owns Room-backed implementations of meal domain repository
  interfaces.

Callers outside `meal/data/` should depend on the domain repository interfaces
in `features/meal/domain/`, not on Room entities or DAOs.

## Entry points

- `MealRoomRepository` implements household, preference, plan, consumption, and
  weekly-food-target persistence.
- `MealRecipeRoomRepository` implements recipe and ingredient persistence.
- `MealPantryRoomRepository` implements pantry and shopping-list persistence.

## Placement convention

Place new meal data-layer files in exactly one of these packages:

- `entity/` for persisted Room shapes
- `dao/` for Room access methods and source-local projections
- `repository/` for concrete repository adapters and mapping coordination

Use a new `api/` package only for a real remote source or credential boundary.
