# meal/application/internal

Internal helpers for the meal planning application layer. Not part of the public API surface.

## Modules

### EntityLookupHelper
A utility class for repository queries that must find an entity (mandatory lookups).

**When to use:** In use-case methods when querying for an entity by ID and the presence is required.

**How to use:**
```java
Ingredient ingredient = EntityLookupHelper.requireFound(
    recipeRepository.getIngredient(ingredientId),
    "Ingredient",
    ingredientId
);
```

Throws `IllegalArgumentException` if the entity is not found, with a message that includes the entity type and ID.

### LegacyMealImportService
One-shot migration tool for importing meal data from legacy application snapshots.

**When to use:**
- Data migration from a previous version of AutoSecretary.
- One-time import of legacy meal snapshots into the current database.
- **NOT** for ongoing, repeated imports of fresh data (create a separate service instead).

**How it works:**
1. Construct a `sourceRows` Map with legacy entity data.
2. Call `importOnce(sourceRows)`.
3. The service processes all entity types sequentially (ingredients → recipes → meal plans → etc.).
4. Returns a `LegacyImportReport` with per-entity-type success counts and a list of failures.

**Input structure (sourceRows):**

The `sourceRows` parameter is a `Map<String, List<Map<String, Object>>>` where:
- **Keys** are entity type identifiers (use the `SOURCE_*` constants):
  - `LegacyMealImportService.SOURCE_INGREDIENTS`
  - `LegacyMealImportService.SOURCE_RECIPES`
  - `LegacyMealImportService.SOURCE_MEAL_PLANS`
  - `LegacyMealImportService.SOURCE_CONSUMPTION`
  - `LegacyMealImportService.SOURCE_PANTRY`
  - `LegacyMealImportService.SOURCE_SHOPPING`
  - `LegacyMealImportService.SOURCE_MEMBERS`
  - `LegacyMealImportService.SOURCE_PREFERENCES`
  - `LegacyMealImportService.SOURCE_WEEKLY_TARGETS`

- **Values** are lists of entity data Maps, where each Map contains column keys and values.

**Example:**
```java
Map<String, List<Map<String, Object>>> sourceRows = new HashMap<>();

// Ingredients: required keys = "name"; optional keys = "food_group", "default_unit", "calories_per_100", etc.
sourceRows.put(LegacyMealImportService.SOURCE_INGREDIENTS, List.of(
    Map.of("id", 1L, "name", "Tomato", "food_group", "VEGETABLE", "calories_per_100", 18),
    Map.of("id", 2L, "name", "Chicken", "food_group", "MEAT", "calories_per_100", 165)
));

// Recipes: required keys = "title"; optional = "description", "instructions", "prep_time_minutes", etc.
sourceRows.put(LegacyMealImportService.SOURCE_RECIPES, List.of(
    Map.of("id", 1L, "title", "Tomato Soup", "prep_time_minutes", 15, "servings", 4)
));

// ... add other entity types as needed

LegacyImportReport report = legacyService.importOnce(sourceRows);

// Inspect results
System.out.println("Imported: " + report.migratedBySource()); // e.g., {SOURCE_INGREDIENTS: 2, SOURCE_RECIPES: 1}
if (!report.failures().isEmpty()) {
    report.failures().forEach(f -> System.err.println("Row " + f.rowIndex() + " failed: " + f.reason()));
}
```

**Data type handling:**
- **Enum values:** Matched case-insensitively (e.g., `"vegetable"`, `"Vegetable"`, `"VEGETABLE"` all match `Ingredient.FoodGroup.VEGETABLE`).
  Unknown enum values log a warning and use the entity's default value.
- **Dates:** Accepted in multiple formats:
  - ISO-8601: `2024-12-31` or `2024-12-31T14:30:00`
  - Legacy format: `dd.MM.yyyy` (e.g., `31.12.2024`)
  - Legacy format: `yyyy/MM/dd` (e.g., `2024/12/31`)
  - Epoch seconds (converted to LocalDate).
- **Missing/unparseable required fields:** The entire row is rejected with a validation error message.
- **Missing/unparseable optional fields:** Assigned a sensible default (e.g., `Ingredient.FoodGroup.OTHER`, or null).

**Error handling:**
- **Idempotent:** The service can only import once. A second call to `importOnce()` returns immediately with an error report.
- **Partial import:** If one entity type fails, others are processed anyway. No rollback occurs.
- **Failures list:** Check `report.failures()` to see which rows could not be migrated and why.

**Related sources:**
- Legacy format reference: `history/migrating/entities/*` (see git history for version being migrated)
- Legacy parser reference: `history/migrating/repository/parser/*`

---

**Technical notes for contributors:**
- Parsing helpers (asEnum, asDate, asDateTime, asLong, etc.) are defined locally and are NOT shared with `meal.data.internal.mapper.MapperSupport` to preserve different error semantics (legacy = lenient/logged; data layer = strict/exception-throwing).
- Repository saves are not transactional; partial data is committed on partial failure. This is acceptable for a one-shot migration.
