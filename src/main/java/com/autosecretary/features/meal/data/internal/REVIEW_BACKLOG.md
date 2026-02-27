# Review Backlog — meal/data/internal

## Open Issues

### [coupling] Untyped `Map<String, Object>` row representation across the entire data layer
**Files:** `storage/MealStorage.java:7-11`, all `mapper/*RowMapper.java`, `dao/BaseCollectionDao.java`

`Map<String, Object>` as the row representation is untyped primitive obsession: field names are magic strings, values require runtime casts, and errors surface only at runtime. Every RowMapper and DAO caller is implicitly coupled to these string keys with no compile-time safety. Fixing requires replacing the raw-map API with a typed Row/Cursor abstraction across the entire data.internal layer (storage, RowMapper, all DAOs) — scope too large for a single run.

**Suggested alternative:** Replace the `Map<String, Object>` row contract with a typed `Row` wrapper that provides `getString(key)`, `getInt(key)`, etc. This touches every mapper, the storage interface, and the DAO — defer to a dedicated migration.

### [consider] Duplicate `FIELD_PERIOD_KEY` constant across two mappers, imported by two repositories
**Files:** `mapper/ShoppingListItemRowMapper.java:10`, `mapper/WeeklyFoodTargetRowMapper.java:10`, `repository/StoragePantryRepository.java:7`, `repository/StorageMealRepository.java:10`

Both mappers independently declare:
```java
public static final String FIELD_PERIOD_KEY = "periodKey";
```
The value is identical. `StoragePantryRepository` imports from `ShoppingListItemRowMapper` and `StorageMealRepository` imports from `WeeklyFoodTargetRowMapper`. A rename of the underlying field requires two constant edits plus two import updates across two different sub-packages.

The duplication is low-risk today (both map a field that genuinely belongs to each entity), but if period-key querying grows to a third entity, the pattern will fragment further.

**Suggested alternative:** If the pattern spreads, extract to a shared `MealDataKeys` constants class at the `data.internal` level. No action needed for just two usages.
