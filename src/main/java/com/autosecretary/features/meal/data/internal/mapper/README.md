# Meal Data Mappers

This package contains **RowMapper implementations** that serialize/deserialize domain entities to and from untyped `Map<String, Object>` storage rows.

## Quick Overview

A **RowMapper** bridges the gap between:
- **Domain layer** — strongly-typed entities like `Recipe`, `Ingredient`, `MealPlan` (clean, type-safe)
- **Storage layer** — untyped `Map<String, Object>` rows (flexible, but no compile-time safety)

Each domain model that needs persistence gets a corresponding mapper. The mapper is responsible for:
1. **Serialization** (`toRow()`) — convert domain entity → storage row
2. **Deserialization** (`fromRow()`) — convert storage row → domain entity

## When You Need a Mapper

You need to create a new RowMapper when:
- Adding a new meal domain model (e.g., a new entity in `features/meal/domain/`)
- The model needs to be persisted (stored and retrieved)

## How to Implement a Mapper

### 1. Create the mapper class

```java
package com.autosecretary.features.meal.data.internal.mapper;

import com.autosecretary.features.meal.domain.MyEntity;
import java.util.HashMap;
import java.util.Map;

public class MyEntityRowMapper implements RowMapper<MyEntity> {
    @Override
    public Map<String, Object> toRow(MyEntity entity) {
        // TODO: serialize to storage row
    }

    @Override
    public MyEntity fromRow(Map<String, Object> row) {
        // TODO: deserialize from storage row
    }
}
```

### 2. Add field constants to `MealFieldKeys`

```java
// In MealFieldKeys.java
interface MyEntity {
    String ID = "id";
    String NAME = "name";
    String CREATED_AT = "createdAt";
    // ... one constant per field
}
```

### 3. Implement `toRow()` — Serialization

Convert each field from the domain entity to a storage-compatible value:

```java
@Override
public Map<String, Object> toRow(MyEntity entity) {
    Map<String, Object> row = new HashMap<>();
    row.put(MealFieldKeys.MyEntity.ID, entity.id);
    row.put(MealFieldKeys.MyEntity.NAME, entity.name);
    row.put(MealFieldKeys.MyEntity.CREATED_AT, MapperSupport.toDateString(entity.createdAt));
    // ... map each field
    return row;
}
```

### 4. Implement `fromRow()` — Deserialization

Reconstruct the domain entity from the storage row, using `MapperSupport` for safe type conversion:

```java
@Override
public MyEntity fromRow(Map<String, Object> row) {
    MyEntity entity = new MyEntity();
    entity.id = MapperSupport.asNullableLong(row.get(MealFieldKeys.MyEntity.ID));
    entity.name = (String) row.get(MealFieldKeys.MyEntity.NAME);  // raw cast is safe
    entity.createdAt = MapperSupport.asLocalDate(row.get(MealFieldKeys.MyEntity.CREATED_AT));
    // ... deserialize each field
    return entity;
}
```

## MapperSupport Utilities

`MapperSupport` provides safe type conversion methods. Use them in `fromRow()`:

### Nullable conversions (return type or null)
- `asNullableLong(Object)` — parse as `Long` or return null
- `asNullableInt(Object)` — parse as `Integer` or return null
- `asLocalDate(Object)` — parse as `LocalDate` or return null
- `asLocalDateTime(Object)` — parse as `LocalDateTime` or return null

### Primitive conversions with fallback (return primitive or fallback)
- `asInt(Object)` — parse as int, fallback to 0
- `asInt(Object, int fallback)` — parse as int, fallback to provided value
- `asLong(Object)`, `asLong(Object, long fallback)` — similar
- `asDouble(Object)`, `asDouble(Object, double fallback)` — similar
- `asBoolean(Object)`, `asBoolean(Object, boolean fallback)` — similar

### Enum conversions
- `asEnum(Class<E>, Object, E fallback)` — parse a single enum constant by name, fallback if invalid
- `asEnumSet(Class<E>, Object)` — parse a comma-separated list of enum names into a `Set<E>`
  (handles both a native `Set<E>` value and a string representation)

### Date/time serialization
- `toDateString(LocalDate)` — serialize date to ISO string
- `toDateTimeString(LocalDateTime)` — serialize datetime to ISO string
- `enumNameOrNull(Enum)` — serialize a single enum value to its name string, or null
- `serializeEnumSet(Set<? extends Enum<?>>)` — serialize any enum set to a comma-separated name string

### Collection patterns — parsing from string

For list fields that are serialized as delimited strings by `toRow()`:

```java
// Parse from string via a custom parser; passes null to the parser if value is not a String
recipe.ingredients = MapperSupport.parseListFromString(
    row.get(MealFieldKeys.Recipe.INGREDIENTS),
    RecipeRowMapper::parseIngredients  // custom parser for string format
);
```

For enum sets, use `asEnumSet()` directly — it handles both native `Set<E>` values and
comma-separated name strings without needing a custom parser:

```java
// Example: a field storing a Set<SomeMealEnum>
entity.mealTypes = MapperSupport.asEnumSet(SomeMealEnum.class, row.get(MealFieldKeys.MyEntity.MEAL_TYPES));
// Serialize back:
row.put(MealFieldKeys.MyEntity.MEAL_TYPES, MapperSupport.serializeEnumSet(entity.mealTypes));
```

### Special: DayOfWeek sets

`DayOfWeek` is a common enough case that `MapperSupport` provides convenience wrappers:

```java
// Replace "MY_DAYS_FIELD_KEY" with your MealFieldKeys constant — never use a raw string literal.
preferences.cookingDays = MapperSupport.asDayOfWeekSet(row.get(MealFieldKeys.MyCookingPrefs.MY_DAYS_FIELD_KEY));
// Serialize back (use serializeEnumSet directly — no DayOfWeek-specific wrapper needed):
row.put(MealFieldKeys.MyCookingPrefs.MY_DAYS_FIELD_KEY, MapperSupport.serializeEnumSet(preferences.cookingDays));
```

`asDayOfWeekSet` is a convenience wrapper over `asEnumSet(DayOfWeek.class, ...)` that saves the
`Class` argument. For serialization, use `serializeEnumSet` directly for any `Set<? extends Enum>`.
If you need a `Set<SomeOtherEnum>`, use `asEnumSet` / `serializeEnumSet` directly.

## Custom Serialization Formats

For complex nested objects (e.g., `List<StorePackage>`, `List<RecipeIngredient>`), you'll serialize to a delimited string format.

### Delimiter conventions

Use these characters consistently:
- **`|` (pipe)** — field separator within a record
- **`;` (semicolon)** — record separator
- **`,` (comma)** — alternate record separator (for simpler single-field records)

**Critical:** Nested values (field names, user-entered text, enum names) must **not contain these characters**, or parsing will fail silently.

### Example: Serializing a list of complex objects

```java
// Serialize List<StorePackage> to "storeName|unit|amount|price;storeName|unit|amount|price;..."
private static String serializeStorePackages(List<Ingredient.StorePackage> packages) {
    if (packages == null || packages.isEmpty()) return "";
    return packages.stream()
        .map(p -> p.storeName + "|" + p.unit + "|" + p.amount + "|" + p.price)
        .collect(Collectors.joining(";"));
}

// Parse the string back into List<StorePackage>
private static List<Ingredient.StorePackage> parseStorePackages(String raw) {
    List<Ingredient.StorePackage> result = new ArrayList<>();
    if (raw == null || raw.isBlank()) return result;
    for (String entry : raw.split(";")) {
        String[] parts = entry.split("\\|", 4);
        if (parts.length != 4) continue;  // skip malformed entries
        Ingredient.StorePackage pkg = new Ingredient.StorePackage();
        pkg.storeName = parts[0];
        pkg.unit = parts[1];
        pkg.amount = MapperSupport.asInt(parts[2]);
        pkg.price = MapperSupport.asInt(parts[3]);
        result.add(pkg);
    }
    return result;
}
```

Use `MapperSupport.parseListFromString()` to handle the string format:

```java
ingredient.storePackages = MapperSupport.parseListFromString(
    row.get(MealFieldKeys.Ingredient.STORE_PACKAGES),
    IngredientRowMapper::parseStorePackages
);
```

## String fields

String fields use raw casts (no `MapperSupport.asString()` method):
```java
entity.name = (String) row.get(MealFieldKeys.MyEntity.NAME);
```

This is safe because the storage layer always serializes strings via `toRow()`. If storage changes (e.g., to a database), wrap strings in a future `MapperSupport.asString()` method.

## Complete Example

See `RecipeRowMapper.java` for a comprehensive example with:
- Simple scalar fields (strings, numbers, dates)
- Enums (with fallback defaults)
- Enum sets (comma-separated)
- Nested complex objects (custom serialization with delimiters)

## Integration with Repositories

Mappers are used by `BaseCollectionDao` and wired in repository constructors (see `StorageMealRepository`):

```java
mealPlanDao = new BaseCollectionDao<>(
    MealCollections.MEAL_PLANS,      // collection name
    storage,                          // MealStorage instance
    new MealPlanRowMapper(),          // serializer
    mealPlan -> mealPlan.id,          // idAccessor
    (mealPlan, id) -> mealPlan.id = id  // idSetter
);
```

## Naming Convention

- Class name: `<EntityName>RowMapper` (e.g., `RecipeRowMapper`, `IngredientRowMapper`)
- Package: `com.autosecretary.features.meal.data.internal.mapper`
- File name: `<EntityName>RowMapper.java`

## Further Reading

- **`RowMapper.java`** — interface definition and javadoc
- **`MapperSupport.java`** — detailed docs on safe conversion patterns
- **Existing mappers** — examples of common patterns:
  - `RecipeRowMapper.java` — complex nested objects
  - `IngredientRowMapper.java` — store packages serialization
  - `MealPlanRowMapper.java` — simple scalar fields
- **`features/meal/data/internal/repository/README.md`** — how mappers integrate into the data layer
