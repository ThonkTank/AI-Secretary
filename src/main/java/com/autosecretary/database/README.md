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

`AppDatabase` declares all Room entities and DAO abstract methods. Task model entities live in
`features/task/domain/model/` because they are domain-owned; task DAOs stay in
`features/task/data/`. Feature-level DAOs (e.g., `TaskDao`, `BudgetTransactionDao`) define
queries; this package only wires them together.

## Key design decisions

- **No destructive fallback as default.** The app stores real user data, so schema changes must preserve data.
- **DB version.** The single source of truth is `@Database(version = ...)` in `AppDatabase.java`; bump it for any schema change.
- **Schema changes require migrations.** Add compatible Room migrations for every version jump.
- **Single-threaded access.** All database calls run on `AppCompositionRoot.getDbExecutor()` — a single-threaded `ExecutorService`. File and network work runs on `getIoExecutor()`. Results post to the main thread via `Handler`.
- **Lifecycle ownership.** Production calls to `AppDatabase.getInstance()` and `AppDatabase.closeAndReset()` belong to `AppCompositionRoot`; restore/reset services use its lifecycle interface instead of touching the singleton directly.
- **Type converters are global.** `@TypeConverters(Converters.class)` on `AppDatabase` makes all converters available to every DAO without per-DAO annotation.

## Safe schema-change checklist

1. Update entity/DAO schema and bump `@Database(version = ...)`.
2. Implement and register the required Room migration(s) in `Room.databaseBuilder(...).addMigrations(...)`.
3. Verify upgrade from the previous app version using a DB containing real data.
4. Keep destructive fallback disabled; if a migration is missing, fail fast instead of deleting user data.

## Reading order

1. **`AppDatabase.java`** — understand entity registry, DAO surface, singleton pattern, and migration policy.
2. **`Converters.java`** — understand how Java types map to SQLite storage (each type has a `from*`/`to*` pair).
3. Feature DAOs (e.g., `features/task/data/TaskDao.java`) — see how queries use these converters implicitly.

## Further reading

- [Room persistence library (Android docs)](https://developer.android.com/training/data-storage/room)
- [Room type converters](https://developer.android.com/training/data-storage/room/referencing-data#type-converters)
- [Room database versioning](https://developer.android.com/training/data-storage/room/migrating-db-versions)
