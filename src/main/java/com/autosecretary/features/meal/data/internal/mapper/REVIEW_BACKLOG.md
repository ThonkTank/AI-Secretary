# Review Backlog — meal/data/internal/mapper

## Open Issues

### [consider] No `asString` method in MapperSupport
**Files:** All `*RowMapper.java` `fromRow()` methods

All non-String types use safe conversion methods in MapperSupport. String fields use raw `(String)` casts with no safety net. Low practical risk since the data layer always reads what it wrote, but inconsistent with the established safe-conversion pattern.

**Canonical recommendation:** Defer — the raw cast is safe in the current architecture. If the storage layer changes (e.g., to a database), add a `MapperSupport.asString(Object)` method.

## Completed in This Review

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
