# Review Backlog — meal/data/internal/mapper

## Open Issues

### [consider] No `asString` method in MapperSupport
**Files:** All `*RowMapper.java` `fromRow()` methods

All non-String types use safe conversion methods in MapperSupport. String fields use raw `(String)` casts with no safety net. Low practical risk since the data layer always reads what it wrote, but inconsistent with the established safe-conversion pattern.

**Canonical recommendation:** Defer — the raw cast is safe in the current architecture. If the storage layer changes (e.g., to a database), add a `MapperSupport.asString(Object)` method.

## Completed in This Review

✅ **[nit] Fixed null round-trip bug in RecipeRowMapper ingredient serialization** (RecipeRowMapper.java:112-134)
- `serializeIngredients` now wraps `ingredientName` and `unit` in `Objects.toString(..., "")` instead of raw concatenation (null → `""` not `"null"`).
- `parseIngredients` now applies `parts[N].isEmpty() ? null : parts[N]` for `ingredientName` and `unit`, matching the pattern in `IngredientRowMapper.parseStorePackagesFromString`.
- Null round-trip is now consistent: null serializes as `""` and deserializes back as null, matching how `ingredientId` and `IngredientRowMapper`'s `storeName`/`unit` are handled.

✅ **[warning] Eliminated duplicated field-serialization patterns in WeeklyFoodTargetRowMapper** (WeeklyFoodTargetRowMapper.java:toRow:24-27, fromRow:36-39)
- Extracted all 11 food group field pairs (*Grams and *Planned) into a FoodGroupFields helper class with 4 static methods (serializeGrams, serializePlanned, deserializeGrams, deserializePlanned).
- Reduced toRow() and fromRow() from 28 and 28 lines of repetitive code down to 2-line method calls each.
- New additions of food groups now require only 2 method additions, not 4 field line updates in both methods.
- Fixed: WeeklyFoodTargetRowMapper.java (lines 10-120)

✅ **[warning] Eliminated duplicated field-serialization patterns in CookingPreferencesRowMapper** (CookingPreferencesRowMapper.java:toRow:25-32, fromRow:41-48)
- Extracted all 4 meal type field pairs (max*Cooking and *CookingDays) into a MealTypeFields helper class with 4 static methods.
- Reduced both toRow() and fromRow() from 10 and 10 lines of repetitive code down to 2-line method calls each.
- Improved maintainability: new meal types now require only 2 method additions.
- Fixed: CookingPreferencesRowMapper.java (lines 20-64)

✅ **[nit] Added MapperSupport.asNullableInt() method for consistent null-safe integer parsing** (MapperSupport.java:93-98)
- Implements same pattern as asNullableLong: null input → null result, empty string → null, valid number → parse, invalid → NumberFormatException.
- Fixes IngredientRowMapper.parseStorePackagesFromString to use explicit nullable conversion instead of conditional ternary for priceCents field.
- Improved: IngredientRowMapper.java line 89 now uses asNullableInt instead of conditional logic.

✅ **[friction] MapperSupport lacks class-level javadoc** (MapperSupport.java:13)
- Added 60+ lines of comprehensive class-level javadoc explaining purpose, design philosophy, all conversion patterns, example usage, and delimiter conventions for custom serialization.

✅ **[friction] RowMapper interface lacks javadoc** (RowMapper.java:5-8)
- Added interface-level and method-level javadoc explaining serialization contract, responsibilities, integration with BaseCollectionDao and MealStorage, and references to MapperSupport and MealFieldKeys.

✅ **[friction] No README in mapper/ directory** (README.md created)
- Created comprehensive 280+ line README explaining when/why to create mappers, step-by-step implementation guide with code examples, MapperSupport utilities, both-paths patterns, custom serialization with delimiter conventions, string field casting rationale, complete working example (RecipeRowMapper), and naming conventions.

✅ **[comment] Raw String casts lack explanation** (6 mappers updated)
- Added explanatory comments to `fromRow()` methods in:
  - IngredientRowMapper.java:39
  - RecipeRowMapper.java:54
  - HouseholdMemberRowMapper.java:29
  - MealPlanRowMapper.java:40
  - PantryItemRowMapper.java:29
  - ShoppingListItemRowMapper.java:34
  - WeeklyFoodTargetRowMapper.java:45

✅ **[comment] Concrete mapper classes lack class-level javadoc** (all 9 `*RowMapper.java`)
- Added class-level javadoc to all 9 concrete mapper classes explaining which domain entity
  each handles and calling out non-obvious behavior: delimited string serialization in
  `RecipeRowMapper` and `IngredientRowMapper`, singleton semantics in `CookingPreferencesRowMapper`,
  shared `PERIOD_KEY` in `ShoppingListItemRowMapper` and `WeeklyFoodTargetRowMapper`, and the
  `*Grams`/`*Planned` dual-field pattern in `WeeklyFoodTargetRowMapper`.

✅ **[docs] Delimiter conventions scattered across comments**
- Centralized delimiter conventions (`|`, `;`, `,`) in MapperSupport javadoc with critical validation warning.
- Expanded README with "Custom Serialization Formats" section including delimiter rules, example serialization/parsing code, and both-paths pattern usage.
- Existing NOTE comments in mappers remain but now readers are directed to the centralized documentation first.
