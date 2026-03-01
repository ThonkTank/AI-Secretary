# Meal Data Layer

This package is the **data layer** for the meal feature. It adapts raw in-memory storage to the
domain repository interfaces (`MealRepository`, `PantryRepository`, `RecipeRepository`).

## Package boundary

```
meal/data/
└── internal/   ← all implementation lives here; nothing here is part of the public API
```

Callers outside `meal/data/` should only depend on the domain repository interfaces in
`features/meal/domain/` (e.g. `MealRepository`). The `internal/` sub-package is an implementation
detail and must not be imported from outside `meal/data/`.

**Important:** The meal data layer uses `InMemoryMealStorage` — all data is lost when the
process dies. This is by design; the meal feature does not use Room.

## Where to start

→ **[`internal/README.md`](internal/README.md)** — full description of the data flow, all files,
and a reading order for understanding how the layer works end to end.
