# Review Backlog — meal/data/internal

## Open Issues

### [coupling] Untyped `Map<String, Object>` row representation across the entire data layer
**Files:** `storage/MealStorage.java:7-11`, all `mapper/*RowMapper.java`, `dao/BaseCollectionDao.java`

`Map<String, Object>` as the row representation is untyped primitive obsession: field names are magic strings, values require runtime casts, and errors surface only at runtime. Every RowMapper and DAO caller is implicitly coupled to these string keys with no compile-time safety. Fixing requires replacing the raw-map API with a typed Row/Cursor abstraction across the entire data.internal layer (storage, RowMapper, all DAOs) — scope too large for a single run.

**Suggested alternative:** Replace the `Map<String, Object>` row contract with a typed `Row` wrapper that provides `getString(key)`, `getInt(key)`, etc. This touches every mapper, the storage interface, and the DAO — defer to a dedicated migration.
