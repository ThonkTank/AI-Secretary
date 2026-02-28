# Review Backlog — meal/data/internal

## Open Issues

### [coupling] Untyped `Map<String, Object>` row representation across the entire data layer
**Files:** `storage/MealStorage.java:7-11`, all `mapper/*RowMapper.java`, `dao/BaseCollectionDao.java`

`Map<String, Object>` as the row representation is untyped primitive obsession: field names are magic strings, values require runtime casts, and errors surface only at runtime. Every RowMapper and DAO caller is implicitly coupled to these string keys with no compile-time safety. Fixing requires replacing the raw-map API with a typed Row/Cursor abstraction across the entire data.internal layer (storage, RowMapper, all DAOs) — scope too large for a single run.

**Suggested alternative:** Replace the `Map<String, Object>` row contract with a typed `Row` wrapper that provides `getString(key)`, `getInt(key)`, etc. This touches every mapper, the storage interface, and the DAO — defer to a dedicated migration.

---

### ✅ [FIXED] Constructor parameter count + fragile lambda pattern
**Files:** `BaseCollectionDao.java:57-66`, `EntityIdHandler.java` (new), `repository/StorageMealRepository.java:50-54`, `repository/StoragePantryRepository.java:31-32`, `repository/StorageRecipeRepository.java:30-31`

*(Promoted from dao/REVIEW_BACKLOG.md)*

**Resolution:** Created `EntityIdHandler<T>` interface with `getId(T)` and `setId(T, Long)` methods. Updated `BaseCollectionDao` to accept a single `EntityIdHandler<T>` parameter instead of separate `idAccessor` and `idSetter` lambdas. Reduced constructor parameters from 5 to 4. Created `EntityIdHandler.of(getter, setter)` factory method to wrap lambdas. Updated all three repositories to use the new pattern.

**Impact:**
- Constructor parameter count reduced from 5 to 4
- Id-handling logic now centralized and more discoverable
- Eliminated repetitive lambda boilerplate across 9 DAO instantiations
- No behavior changes; build verified clean

---

### [consider] Full in-memory scan for range queries
**Files:** `BaseCollectionDao.java:68-70`, `repository/StorageMealRepository.java:43,58`

*(Promoted from dao/REVIEW_BACKLOG.md)*

`findAll(Predicate<T>)` always loads every row in the collection and filters in Java. `StorageMealRepository` uses this for date-range queries on `MEAL_PLANS` and `CONSUMPTION_LOGS`. The current `MealStorage` API has no range-query capability (`findByField` supports only equality), so this is the only viable path today. At larger data volumes this will become a bottleneck.

**Suggested alternative:** No action needed while data volumes are small. If meal data grows substantially, extend `MealStorage` with a range-query method and push filtering into the storage layer.

## Acknowledged Good Patterns

### [keep] `MealCollections.java` at `internal/` root
**File:** `MealCollections.java`

Collection name constants live at the root of `internal/`, not inside `dao/` or `repository/`. This is the right call: they're shared by all three repository classes, and placing them at the common ancestor makes them discoverable without importing from a sibling package.

### [keep] `MealFieldKeys.java` alongside `MealCollections.java` at `internal/` root
**File:** `MealFieldKeys.java`

Field name constants were moved from `mapper/` to `internal/` root so that both "what collections are called" (`MealCollections`) and "what fields within collections are called" (`MealFieldKeys`) live together as peer schema-constant files. This removes the unexpected `repository/ → mapper/` import dependency and makes the `mapper/` package purely about serialization logic.

### [keep] `BaseCollectionDao.java` at `internal/` root
**File:** `BaseCollectionDao.java`

`BaseCollectionDao` was moved from a single-file `dao/` sub-folder to the `internal/` root. It now sits alongside `MealCollections` and `MealFieldKeys` as peer infrastructure files used across the whole data layer. Eliminating the `dao/` folder removes one navigation hop for a class with no sibling files.
