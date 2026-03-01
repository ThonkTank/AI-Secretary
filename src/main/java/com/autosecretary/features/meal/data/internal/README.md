# Meal Data — Internal Implementation Layer

This package is the private implementation of the meal feature's data layer. It is not part of
the public API — callers outside `features/meal/data/` interact only with the domain repository
interfaces (`MealRepository`, `PantryRepository`, `RecipeRepository`).

## What lives here

```
meal/data/internal/
├── BaseCollectionDao.java    — Generic CRUD DAO (the central abstraction)
├── MealCollections.java      — Storage collection-name constants (one per entity type)
├── MealFieldKeys.java        — Field-name constants for Map<String,Object> rows
├── mapper/                   — RowMapper implementations (entity ↔ Map serialization)
│   └── README.md             — How to read/write mappers
├── repository/               — StorageFooRepository implementations
│   └── README.md             — How repositories compose DAOs + mappers
└── storage/                  — MealStorage interface + InMemoryMealStorage
```

## Data flow

A domain call like `mealRepository.getMealPlans(from, to)` travels through four layers:

```
1. StorageMealRepository         — translates domain query to DAO call
        ↓
2. BaseCollectionDao<MealPlan>   — generic CRUD: delegates to storage, uses mapper
        ↓
3. MealPlanRowMapper             — converts Map<String,Object> ↔ MealPlan
        ↓
4. InMemoryMealStorage           — stores rows as Map<Long, Map<String,Object>> per collection
```

## The three root files

### `BaseCollectionDao<T>`
The central workhorse. Every repository owns one `BaseCollectionDao` per entity type. It:
- Calls `MealStorage` to read/write raw rows
- Uses a `RowMapper<T>` to convert between typed entities and untyped rows
- Handles id generation: if an entity has no id yet, storage auto-generates one and the DAO
  injects it back into the entity

See `BaseCollectionDao.java` for javadoc including why lambdas are used for id access.

### `MealCollections`
String constants that name each storage "collection" (analogous to table names). Each entity
type has exactly one collection. Required when creating a new `BaseCollectionDao` or when
adding a new entity type to the data layer.

### `MealFieldKeys`
String constants for every field within every entity's storage row. Organised as nested
interfaces — one per entity type — so usages are always qualified:
`MealFieldKeys.Recipe.TITLE` rather than a flat global name. The top-level `PERIOD_KEY`
is shared across two entity types (`ShoppingListItem` and `WeeklyFoodTarget`).

## Sub-packages

### `mapper/`
One `*RowMapper` class per entity. Each implements `RowMapper<T>` with `toRow()` and
`fromRow()`. Start with `mapper/README.md` if you need to add or modify a mapper.

### `repository/`
Three `Storage*Repository` classes that implement the domain repository interfaces:
- `StorageMealRepository` — meal plans, consumption logs, household members, preferences, weekly targets
- `StoragePantryRepository` — pantry items, shopping list items
- `StorageRecipeRepository` — recipes, ingredients

See `repository/README.md` for how repositories compose DAOs and the BaseCollectionDao pattern.

### `storage/`
`MealStorage` interface and `InMemoryMealStorage` implementation. The storage layer is untyped —
it knows nothing about domain entities, only `Map<String, Object>` rows.

## Where to start reading

If you want to understand the full data flow, read in this order:
1. `MealStorage.java` — the raw storage contract
2. `BaseCollectionDao.java` — how CRUD operations are composed
3. `mapper/RowMapper.java` — the serialization contract
4. Any one `*RowMapper.java` (e.g. `MealPlanRowMapper`) — a concrete example
5. Any one `Storage*Repository.java` (e.g. `StorageMealRepository`) — how it all wires together
