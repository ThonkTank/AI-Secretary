[warning] storage/MealStorage.java:7-11 — `Map<String, Object>` as the row representation is untyped primitive obsession: field names are magic strings, values require runtime casts, and errors surface only at runtime. Every RowMapper and DAO caller is implicitly coupled to these string keys with no compile-time safety. Fixing requires replacing the raw-map API with a typed Row/Cursor abstraction across the entire data.internal layer (storage, RowMapper, all DAOs) — scope too large for a single run.

---

[consider] storage/MealStorage.java — Interface has only one implementation (`InMemoryMealStorage`). The indirection may be justified if a Room-backed implementation is planned as a future migration target; leave as-is unless the in-memory approach is confirmed permanent.

