# Review Backlog — meal/data/internal

## Open Issues

### [consider] `RowMapper` interface lives in `mapper/` but is the type-parameter contract for `BaseCollectionDao` at the root level
**Files:** `BaseCollectionDao.java`, `mapper/RowMapper.java`, `repository/Storage*.java`

`RowMapper` is the central serialization contract consumed by `BaseCollectionDao` (root level) and wired by all three repositories. Its current location in `mapper/` means `BaseCollectionDao` imports from a child package — an uncommon direction in the module graph. Moving it to `internal/` root would co-locate the interface with its primary consumer (`BaseCollectionDao`) and make the core DAO pattern immediately visible at the top level.

**Counter-argument:** Java convention often keeps an interface near its implementations (`mapper/RowMapper.java` beside `mapper/*RowMapper.java`). The current placement follows this pattern, and the `mapper/README.md` documents `RowMapper` prominently. Low severity — defer unless other structural work touches this area.

### [coupling] Untyped `Map<String, Object>` row representation across the entire data layer
**Files:** `storage/MealStorage.java:7-11`, all `mapper/*RowMapper.java`, `BaseCollectionDao.java`

`Map<String, Object>` as the row representation is untyped primitive obsession: field names are magic strings, values require runtime casts, and errors surface only at runtime. Every RowMapper and DAO caller is implicitly coupled to these string keys with no compile-time safety. Fixing requires replacing the raw-map API with a typed Row/Cursor abstraction across the entire data.internal layer (storage, RowMapper, all DAOs) — scope too large for a single run.

**Suggested alternative:** Replace the `Map<String, Object>` row contract with a typed `Row` wrapper that provides `getString(key)`, `getInt(key)`, etc. This touches every mapper, the storage interface, and the DAO — defer to a dedicated migration.

### [nit] `saveCookingPreferences` mutates the caller's argument
**File:** `repository/StorageMealRepository.java:133`

`saveCookingPreferences(CookingPreferences preferences)` sets `preferences.id = SINGLETON_PREFERENCES_ID` before persisting, silently mutating the caller's object. Intent (enforce singleton id=1) is clear, but surprising at call sites. Low risk since the singleton contract is the intended behavior and callers never set a different id.
