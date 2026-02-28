# Meal Data Repository Layer

## Overview

This package contains **repository implementations** that adapt the meal feature's underlying storage to domain-focused interfaces. The repositories are the primary public entry point to the meal data layer.

## Architecture

```
Domain Interface (e.g., MealRepository)
    ↓
Repository Implementation (e.g., StorageMealRepository)
    ↓
BaseCollectionDao (generic CRUD + filtering)
    ↓
RowMapper (serialization/deserialization)
    ↓
MealStorage (untyped Map<String, Object> CRUD)
```

## Three Repositories

### 1. StorageMealRepository
Manages domain models related to meals and household members:
- **MealPlan** — planned meals for a date
- **ConsumptionLog** — actual meals consumed (for nutrient tracking)
- **HouseholdMember** — person in the household
- **CookingPreferences** — cooking schedule/constraints (singleton: id=1)
- **WeeklyFoodTarget** — weekly nutrient targets

### 2. StoragePantryRepository
Manages inventory and shopping:
- **PantryItem** — pantry stock (what ingredients are on hand)
- **ShoppingListItem** — shopping list entries

### 3. StorageRecipeRepository
Manages recipes and ingredients:
- **Recipe** — recipe with instructions and nutrition info
- **Ingredient** — ingredient definition (used in recipes and pantry)

## Key Design Patterns

### BaseCollectionDao Pattern
Each repository instantiates `BaseCollectionDao<T>` for each domain type. The DAO is parameterized with:
- **Collection name** (from `MealCollections`) — the storage key
- **RowMapper** — converts between domain entity `T` and `Map<String, Object>` (via `fromRow()` and `toRow()`)
- **idAccessor** — lambda to read entity's id for upsert
- **idSetter** — lambda to inject generated ids back into the entity

```java
mealPlanDao = new BaseCollectionDao<>(
    MealCollections.MEAL_PLANS,           // collection name
    storage,                              // MealStorage instance
    new MealPlanRowMapper(),              // serializer
    mealPlan -> mealPlan.id,              // idAccessor: get
    (mealPlan, id) -> mealPlan.id = id    // idSetter: set generated id
);
```

This design allows `BaseCollectionDao` to work with any entity type without reflection, relying on function parameters instead.

### Untyped Storage Abstraction
The underlying `MealStorage` interface works with `Map<String, Object>` rows. Field names are magic strings defined in `MealFieldKeys.java`. RowMappers handle the serialization:
```java
// RowMapper reads fields by string key
String name = row.get(MealFieldKeys.HouseholdMember.NAME);
```

This is a known code smell (primitive obsession + no compile-time safety), but it's acceptable while data volumes and feature complexity are low. If meal data or domain complexity grows, consider migrating to a typed `Row` abstraction.

### Date Range Queries
Queries like `getMealPlans(fromDate, toDate)` use `findAll(Predicate<T>)`, which loads all records and filters in Java:
```java
return mealPlanDao.findAll(plan -> isDateInRange(plan.date, from, to));
```

This is fine for small datasets. At larger scales, extend `MealStorage` with a range-query method and push filtering into storage.

### Singleton Pattern for CookingPreferences
`CookingPreferences` is a singleton — only one row (id=1) is ever persisted. The repository enforces this by always using `SINGLETON_PREFERENCES_ID`:
```java
public CookingPreferences getCookingPreferences() {
    CookingPreferences preferences = cookingPreferencesDao.findById(SINGLETON_PREFERENCES_ID);
    return Objects.requireNonNullElse(preferences, new CookingPreferences());
}
```

On first run, the table is empty, so a new default instance is returned. Callers never have to check for null.

## How to Add a New Domain Type

If you need to persist a new meal domain model:

1. **Define the domain entity** in `features/meal/domain/` (e.g., `FoodLog.java`)
2. **Create a RowMapper** in `features/meal/data/internal/mapper/` (e.g., `FoodLogRowMapper.java`)
   - Implement `fromRow(Map)` to deserialize domain entity
   - Implement `toRow(entity)` to serialize to Map
3. **Add field constants** to `MealFieldKeys.java` (e.g., `interface FoodLog { String ID = "id"; ... }`)
4. **Add a collection name** to `MealCollections.java` (e.g., `String FOOD_LOGS = "foodLogs"`)
5. **Update the appropriate repository** or create a new one:
   - Add a `BaseCollectionDao<FoodLog>` instance in the constructor
   - Implement query/mutation methods to delegate to the DAO

## Dependencies

- **BaseCollectionDao** — generic DAO for all CRUD + filtering
- **MealStorage** — untyped in-memory storage contract
- **MealFieldKeys** — field name constants (magic strings)
- **MealCollections** — collection name constants
- **RowMapper** — serialization abstraction

All of these live at `features/meal/data/internal/` for convenient co-location.

## Further Reading

- **`BaseCollectionDao.java`** — explains DAO lifecycle, id generation, and filtering
- **`MealStorage.java`** — storage contract (find, upsert, delete)
- **`InMemoryMealStorage.java`** — current in-memory implementation
- **Domain models** in `features/meal/domain/` — what these repositories persist
