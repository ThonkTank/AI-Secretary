# Meal Feature

Meal planning, recipe management, pantry tracking, and shopping lists for the household.
Backed by an **in-memory storage layer** (not Room) — all meal data is lost when the process dies.
Data is seeded on first use via the demo recipe in `MealPlannerPresenter`.

## Layer Overview

```
UI (Fragment)
    ↓ calls
Application (Presenter + Use Cases)
    ↓ calls
Domain (Entities + Stateless Services + Repository Interfaces)
    ↑ implemented by
Data (Storage Repositories → RowMappers → InMemoryMealStorage)
```

## Sub-packages

| Package | What lives here | README |
|---|---|---|
| `domain/` | Core entities (`Recipe`, `Ingredient`, `MealPlan`, `PantryItem`, …), enums (`MealType`, `FoodGroup`), stateless services, repository interfaces. No Android dependencies. | [`domain/README.md`](domain/README.md) |
| `application/` | `MealPlannerPresenter` (UI coordinator), cross-feature integration (`TaskMealIntegrationService`). | [`application/README.md`](application/README.md) |
| `data/` | Repository implementations, row mappers, and `InMemoryMealStorage`. All implementation is in `data/internal/`; nothing there is public API. | [`data/README.md`](data/README.md) |
| `ui/` | `MealPlannerFragment` — three-tab UI (Week Plan, Recipes, Stock & Shopping). Purely presentational. | [`ui/README.md`](ui/README.md) |

## Recommended Reading Order

1. **This file** — understand the layers and where things live.
2. **[`domain/README.md`](domain/README.md)** — key concepts (DGE, MHD, nutrition encoding, periodKey) and entity reading order.
3. **[`application/README.md`](application/README.md)** — entry points and how the UI interacts with domain logic.
4. **[`data/README.md`](data/README.md)** → **[`data/internal/README.md`](data/internal/README.md)** — data flow from repositories to in-memory storage.
5. **[`ui/README.md`](ui/README.md)** — fragment structure, dialog pattern, and manual testing steps.

## Key Concepts (Quick Reference)

- **DGE** — Deutsche Gesellschaft fur Ernahrung; German nutrition society reference values for weekly food intake.
- **MHD** — Mindesthaltbarkeitsdatum (best-before date); expiry date is the *last valid day*.
- **periodKey** — ISO date string (`"2026-02-14"`) used as a storage key for shopping lists and weekly targets.
- **x10 fixed-point** — Ingredient macro fields (`proteinPer100`, etc.) are stored as integers scaled by 10 (125 = 12.5 g). Calories are plain kcal (no scaling).
- **InMemoryMealStorage** — All meal data is volatile; it does not persist across app restarts.

See [`domain/README.md`](domain/README.md) for detailed explanations.
