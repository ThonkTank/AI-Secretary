# shared/database - Database Schema Constants

**Purpose:** Centralized database schema constants for SQLite operations.

**Status:** Fully converted to Kotlin (Wave 1 complete ✅)

**Architecture:** Shared utility - Used by data layer and legacy database code

---

## Directory Structure

```
shared/database/
├── DatabaseConstants.kt      # ✅ KOTLIN (Wave 1) - Schema constants object (46 lines)
└── CLAUDE.md                 # This file
```

**Total:** 1 file (complete)
**Converted:** DatabaseConstants.kt (Wave 1)

---

## Purpose and Goals

**Problem:** Database schema constants scattered across codebase, risk of typos and inconsistencies.

**Solution:** Single source of truth - all table names, column names, and schema metadata in one Kotlin object.

**Goals:**
1. **DRY Principle:** Define schema constants once, use everywhere
2. **Type Safety:** Compile-time checking of constant names
3. **Discoverability:** All schema info in one place
4. **Migration Safety:** Easy to update schema version and constants together

**Used By:**
- TaskDatabaseHelper.java (legacy SQL operations)
- Future: Room DAOs and entities (Phase 4.5.4+)

---

## Database Schema (Version 5)

### Tables

**1. tasks** (17 columns)
- **Basic Fields:**
  - `id` (INTEGER PRIMARY KEY)
  - `title` (TEXT NOT NULL)
  - `description` (TEXT)
  - `category` (TEXT)
  - `created_at` (INTEGER)
  - `due_date` (INTEGER)
  - `is_completed` (INTEGER)
  - `priority` (INTEGER)

- **Recurrence Fields:**
  - `recurrence_type` (TEXT) - "INTERVAL" or "FREQUENCY"
  - `recurrence_amount` (INTEGER) - Number (e.g., 3 in "3 days")
  - `recurrence_unit` (TEXT) - "DAY", "WEEK", "MONTH", "YEAR"
  - `last_completed_date` (INTEGER)

- **Period Tracking:**
  - `completions_this_period` (INTEGER)
  - `current_period_start` (INTEGER)

- **Streak Tracking:**
  - `current_streak` (INTEGER)
  - `longest_streak` (INTEGER)
  - `last_streak_date` (INTEGER)

**2. completions** (6 columns)
- `completion_id` (INTEGER PRIMARY KEY)
- `task_id` (INTEGER, FOREIGN KEY → tasks.id)
- `completed_at` (INTEGER)
- `time_spent_minutes` (INTEGER)
- `difficulty` (INTEGER, 0-10 scale)
- `notes` (TEXT)

**Code Reference:** `DatabaseConstants.kt:5-44`

---

## Important Constants

### Database Metadata

```kotlin
DATABASE_NAME = "taskmaster.db"
DATABASE_VERSION = 5
```

**Version History:**
- v1: Initial schema (tasks table only)
- v2: Added recurrence fields
- v3: Added period tracking
- v4: Added streak tracking
- v5: Added completions table

**Migration:** Handled by TaskDatabaseHelper.onUpgrade() (currently Java, will migrate to Room in Phase 4.5.4)

**Code Reference:** `DatabaseConstants.kt:5-6`

---

### Table Names

```kotlin
TABLE_TASKS = "tasks"
TABLE_COMPLETIONS = "completions"
```

**Usage:**
```kotlin
// Query
db.query(DatabaseConstants.TABLE_TASKS, ...)

// Insert
db.insert(DatabaseConstants.TABLE_TASKS, null, values)
```

**Code Reference:** `DatabaseConstants.kt:8-9`

---

### Column Names

**Tasks Table Columns:**
```kotlin
COLUMN_ID = "id"
COLUMN_TITLE = "title"
COLUMN_DESCRIPTION = "description"
// ... (17 total)
```

**Completions Table Columns:**
```kotlin
COLUMN_COMPLETION_ID = "completion_id"
COLUMN_TASK_ID = "task_id"
COLUMN_COMPLETED_AT = "completed_at"
// ... (6 total)
```

**Usage:**
```kotlin
// Get column value
val title = cursor.getString(cursor.getColumnIndex(DatabaseConstants.COLUMN_TITLE))

// ContentValues
values.put(DatabaseConstants.COLUMN_TITLE, "My Task")
```

**Code Reference:**
- Tasks columns: `DatabaseConstants.kt:11-27`
- Completions columns: `DatabaseConstants.kt:30-44`

---

## Key Implementation Details

### Kotlin Object (Singleton)

**Before (Java):**
```java
public class DatabaseConstants {
    public static final String DATABASE_NAME = "taskmaster.db";
    public static final int DATABASE_VERSION = 5;
    // ... 46 more lines
}
```

**After (Kotlin):**
```kotlin
object DatabaseConstants {
    const val DATABASE_NAME = "taskmaster.db"
    const val DATABASE_VERSION = 5
    // ... 44 more lines
}
```

**Benefits:**
- `object` = singleton pattern (built-in)
- `const val` = compile-time constants (inlined)
- No constructor needed
- No `public static final` boilerplate
- 48 lines → 46 lines (-4%)

**Code Reference:** `DatabaseConstants.kt:4-48`

---

## Usage Examples

### 1. Create Table (SQL)

```kotlin
val createTasksTable = """
    CREATE TABLE ${DatabaseConstants.TABLE_TASKS} (
        ${DatabaseConstants.COLUMN_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
        ${DatabaseConstants.COLUMN_TITLE} TEXT NOT NULL,
        ${DatabaseConstants.COLUMN_DESCRIPTION} TEXT,
        ...
    )
"""
db.execSQL(createTasksTable)
```

**Where:** TaskDatabaseHelper.onCreate() (currently Java)

---

### 2. Query Tasks

```kotlin
val cursor = db.query(
    DatabaseConstants.TABLE_TASKS,
    null,  // all columns
    "${DatabaseConstants.COLUMN_IS_COMPLETED} = ?",
    arrayOf("0"),  // active tasks
    null, null,
    "${DatabaseConstants.COLUMN_PRIORITY} DESC"
)
```

**Where:** TaskDatabaseHelper.getAllTasks() (currently Java)

---

### 3. Insert Task

```kotlin
val values = ContentValues().apply {
    put(DatabaseConstants.COLUMN_TITLE, "New Task")
    put(DatabaseConstants.COLUMN_DESCRIPTION, "Description")
    put(DatabaseConstants.COLUMN_PRIORITY, 1)
    put(DatabaseConstants.COLUMN_IS_COMPLETED, 0)
}
db.insert(DatabaseConstants.TABLE_TASKS, null, values)
```

**Where:** TaskDatabaseHelper.addTask() (currently Java)

---

### 4. Migration (Future Room)

```kotlin
@Database(
    entities = [TaskEntity::class, CompletionEntity::class],
    version = DatabaseConstants.DATABASE_VERSION
)
abstract class AppDatabase : RoomDatabase() {
    // Room will use DatabaseConstants.DATABASE_VERSION
}
```

**When:** Phase 4.5.4 (Room ORM migration)

---

## Migration Notes (Phase 4.5.3)

**Wave 1 (Complete ✅):**
- Converted DatabaseConstants.java → DatabaseConstants.kt
- Changed from class to object
- Changed from `public static final` to `const val`
- Result: 48 lines → 46 lines (-4%)

**No Breaking Changes:**
- Same constant names
- Same values
- Java code can still access: `DatabaseConstants.DATABASE_NAME`
- Kotlin code uses: `DatabaseConstants.DATABASE_NAME`

**Future (Wave 4 - Room Migration):**
- DatabaseConstants will be used by Room entities
- Column names will map to @ColumnInfo annotations
- Table names will map to @Entity(tableName = ...)
- Version will map to @Database(version = ...)

---

## Testing and Verification

### Manual Verification

```bash
# 1. Build and install app
gh release download "v0.3.28" -p "AISecretary-signed.apk" -D ~/storage/downloads/
termux-open ~/storage/downloads/AISecretary-signed.apk

# 2. Launch app
am start -n com.secretary.helloworld/.MainActivity

# 3. Verify database operations in logs
curl http://localhost:8080/logs | grep -i database

# Expected: No schema errors, normal CRUD operations
```

### Common Issues

**CompileError: Unresolved reference:**
- Ensure using `DatabaseConstants.CONSTANT_NAME`
- Not `DatabaseConstants().CONSTANT_NAME` (no parentheses)
- Object is singleton, no instantiation needed

**Database version mismatch:**
- Check `DATABASE_VERSION` matches actual database version
- Migration logic in TaskDatabaseHelper must handle version changes

---

## Related Documentation

- **[Project CLAUDE.md](../../../../CLAUDE.md)** - Overall architecture
- **[Parent CLAUDE.md](../CLAUDE.md)** - Shared utilities overview
- **[features/statistics/data/CompletionEntity.kt](../../features/statistics/data/CompletionEntity.kt)** - Uses these constants for Room @Entity

**Future Room Migration:** See ROADMAP.md Phase 4.5.4 for:
- TaskDatabaseHelper → Repository + Room DAO
- How DatabaseConstants will be used in Room entities
- Migration from SQLiteOpenHelper to Room

---

**Last Updated:** 2025-11-13
**Status:** Wave 1 complete (fully Kotlin) ✅
**Version:** v0.3.28 (Build 328)
**Package:** com.secretary.helloworld.shared.database
