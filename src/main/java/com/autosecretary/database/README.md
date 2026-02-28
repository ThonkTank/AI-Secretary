# Database Package (`database/`)

Central Room database wiring for AutoSecretary. This package contains the database class and type converters shared by all features.

## Contents

| File | Purpose |
|------|---------|
| `AppDatabase.java` | Room `@Database` class — singleton instance, entity registry, DAO accessors |
| `Converters.java` | Room `@TypeConverter` methods for `LocalDate`, `LocalTime`, enums, `Set<DayOfWeek>`, etc. |

## How it fits in

```
app/AppCompositionRoot  ──creates──▶  AppDatabase.getInstance()
                                           │
                        ┌──────────────────┼──────────────────┐
                        ▼                  ▼                  ▼
                   taskDao()       budgetTransactionDao()   ...other DAOs
                        │                  │
                   features/task/data   features/budget/data/dao
```

`AppDatabase` declares all Room entities and DAO abstract methods. Feature-level DAOs (e.g., `TaskDao`, `BudgetTransactionDao`) define queries; this package only wires them together.

## Key design decisions

- **Destructive migration only.** `fallbackToDestructiveMigration()` drops and recreates all tables on schema changes. Manual `Migration` subclasses are forbidden (see CLAUDE.md). Always back up user data before bumping the DB version.
- **DB version 21.** Bump the version number in `@Database(version = ...)` for any schema change.
- **Single-threaded access.** All database calls run on `AppCompositionRoot.databaseExecutor` — a single-threaded `ExecutorService`. Results post to the main thread via `Handler`.
- **Type converters are global.** `@TypeConverters(Converters.class)` on `AppDatabase` makes all converters available to every DAO without per-DAO annotation.

## Reading order

1. **`AppDatabase.java`** — understand entity registry, DAO surface, singleton pattern, and the destructive-migration policy.
2. **`Converters.java`** — understand how Java types map to SQLite storage (each type has a `from*`/`to*` pair).
3. Feature DAOs (e.g., `features/task/data/TaskDao.java`) — see how queries use these converters implicitly.

## Further reading

- [Room persistence library (Android docs)](https://developer.android.com/training/data-storage/room)
- [Room type converters](https://developer.android.com/training/data-storage/room/referencing-data#type-converters)
- [Room database versioning](https://developer.android.com/training/data-storage/room/migrating-db-versions)
