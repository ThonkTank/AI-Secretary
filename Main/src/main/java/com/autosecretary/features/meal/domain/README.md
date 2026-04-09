# Meal Domain — Developer Guide

This package defines the **core domain model and business logic** for the meal planning feature.
It has no Android dependencies — all classes are plain Java. The data layer (`meal/data/`) and
UI layer (`meal/ui/`) depend on this package; this package depends on nothing else in the app.

## Key Concepts

### DGE (Deutsche Gesellschaft für Ernährung)
The German Nutrition Society publishes recommended daily/weekly intake amounts per food group
for a standard 2000 kcal/day adult diet. These values are the foundation for `WeeklyFoodTarget`
calculations. Reference: [dge.de](https://www.dge.de).

### MHD (Mindesthaltbarkeitsdatum)
German equivalent of "best before date". In this codebase, the expiry date is treated as the
**last valid day** — an item expires strictly *after* the expiry date, not on it.
See `ShelfLifeService.isExpired()` for the exact semantics.

### periodKey
A date-as-ISO-string used as a storage key for time-period-specific records. Examples:
- `"2026-02-14"` — a specific shopping day (used in `ShoppingListItem`)
- `"2026-W08"` — a calendar week (possible future use in `WeeklyFoodTarget`)

The current data layer treats `periodKey` as an opaque string. Callers generate it from
a `LocalDate` or calendar-week computation; the domain classes do not interpret it.

### TrackedItem / itemId
`MealPlan.itemId` and `ConsumptionLog.itemId` both reference a "tracked item" — a logical
completion-tracking entry used by the task feature's checklist system. The `itemId` is an
opaque foreign key stored by the data layer; the meal domain does not depend on the task
feature. When meal plans are not integrated with the task checklist, `itemId` is null.

### Nutrition encoding (×10 fixed point)
Macro fields (`proteinPer100`, `carbsPer100`, `fatPer100`, `fiberPer100`) are stored as
integers scaled by 10 to represent one decimal place. For example, 12.5 g protein is
stored as `125`. Calories (`caloriesPer100`) are stored as plain kcal integers (no scaling).

## Package Structure

```
meal/domain/
├── Ingredient.java               — Core food item; has FoodGroup enum with DGE weekly amounts
├── Recipe.java                   — Recipe with ingredients, scaling bounds, and per-member ratings
├── MealPlan.java                 — A planned meal: links recipe → date + MealType
├── ConsumptionLog.java           — Nutrition log: records what a household member actually ate
├── PantryItem.java               — Pantry inventory entry with expiry tracking
├── ShoppingListItem.java         — Shopping list entry with packaging excess tracking
├── HouseholdMember.java          — Person in the household; used for energy/portion calculations
├── WeeklyFoodTarget.java         — DGE-based weekly food group targets per household
├── MealType.java                 — BREAKFAST / LUNCH / DINNER / SNACK enum
├── CookingPreferences.java       — Per-MealType cooking session limits and allowed days
├── MealRepository.java           — Domain repository interface: meal plans, logs, members, targets
├── PantryRepository.java         — Domain repository interface: pantry, shopping list
├── RecipeRepository.java         — Domain repository interface: recipes, ingredients
├── RecipeScalingService.java     — Stateless service: scales a Recipe to a requested serving count
├── RecipeScalingResult.java      — Result of recipe scaling (factor + scaled ingredient amounts)
├── ShelfLifeService.java         — Stateless service: calculates and checks expiry dates (MHD)
├── ShoppingPackagingService.java — Stateless service: rounds ingredient amounts to package sizes
├── WeeklyFoodTargetService.java  — Stateless service: calculates DGE targets from household members
└── internal/
    ├── HouseholdEnergyService.java — BMR / TDEE / DGE food factor calculations (Mifflin-St Jeor)
    └── MealAmountFormat.java       — Shared amount+unit formatter used by PantryItem and ShoppingListItem
```

## Recommended Reading Order

1. **`MealType.java`** — 4 values, no dependencies. Understand the meal types first.
2. **`Ingredient.java`** — The atomic building block. Read the class header comment on
   nutrition encoding (×10) before reading any nutrition values.
3. **`Recipe.java`** — How ingredients combine into a dish. Note `ScalingPrecision` modes.
4. **`MealPlan.java`** — Links a recipe to a date and meal type. Entry point to the planning flow.
5. **`HouseholdMember.java`** — Used for energy calculations. `ActivityLevel.factor` values are
   standard PAL multipliers.
6. **`WeeklyFoodTarget.java`** — DGE gram targets per food group for the household.
7. **`WeeklyFoodTargetService.java`** and **`internal/HouseholdEnergyService.java`** — How targets
   are calculated from member data (TDEE → DGE food factor → scaled portions).
8. **`PantryItem.java`** and **`ShelfLifeService.java`** — Expiry tracking (MHD semantics).
9. **`ShoppingListItem.java`** and **`ShoppingPackagingService.java`** — Shopping list generation
   with packaging rounding.
10. **`MealRepository.java`**, **`PantryRepository.java`**, **`RecipeRepository.java`** —
    Repository interfaces. Implementations live in `meal/data/internal/repository/`.

## Stateless Services

The four `*Service` classes (`RecipeScalingService`, `ShelfLifeService`,
`ShoppingPackagingService`, `WeeklyFoodTargetService`) are stateless utility classes with
private constructors. Call them via static methods; never instantiate them.

## Executor / Thread Safety

All repository implementations (`StorageMealRepository`, etc.) run on the app's single
shared `ExecutorService` (wired in `AppCompositionRoot`). **Do not call repository methods
on the Android main thread.** Domain model objects themselves are not thread-safe; do not
share mutable instances across threads.
