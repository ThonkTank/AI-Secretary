# app/src/main/java/com/secretary/helloworld - Main Application Code

**Purpose:** Contains all application code - Gradle structure with Kotlin migration in progress.

**Status:** Phase 4.5.3 Wave 1 Complete (14%) - First 3 files converted to Kotlin

**Architecture:** Hybrid - Gradle structure established, Kotlin migration underway (3/18 files done)

**Build System:** Gradle 8.2 + Kotlin 1.9.22 + KSP

---

## Directory Structure (Phase 4.5.3 - Kotlin Migration)

```
app/src/main/java/com/secretary/helloworld/
├── app/                           # ✅ Java (Phase 4.5.2)
│   └── MainActivity.java              # App entry point (Wave 5)
│
├── core/                          # 🚧 CONVERTING (Wave 1: 1/2 Kotlin)
│   ├── logging/
│   │   ├── AppLogger.kt               # ✅ KOTLIN (Wave 1) - Singleton object (122 lines)
│   │   └── HttpLogServer.java         # ⏳ Java (Wave 2) - HTTP server on localhost:8080
│   └── network/
│       ├── UpdateChecker.java         # ⏳ Java (Wave 6) - GitHub Releases API
│       └── UpdateInstaller.java       # ⏳ Java (Wave 6) - APK download
│
├── shared/                        # ✅ KOTLIN (Wave 1: 1/1 done)
│   ├── database/
│   │   └── DatabaseConstants.kt       # ✅ KOTLIN (Wave 1) - Object with const val (46 lines)
│   └── util/                          # (empty)
│
├── features/                      # 🚧 KOTLIN (Wave 1: 1/1 done)
│   ├── tasks/
│   │   ├── data/                      # ⏳ (Wave 4 - Room migration)
│   │   ├── domain/                    # ⏳ (Wave 5 - Use Cases)
│   │   └── presentation/              # ⏳ (Wave 6 - ViewModels)
│   └── statistics/
│       ├── data/
│       │   └── CompletionEntity.kt    # ✅ KOTLIN (Wave 1) - Data class (50 lines)
│       ├── domain/                    # ⏳ (Wave 5)
│       └── presentation/              # ⏳ (Wave 6)
│
└── (root - legacy Java files)     # ⏳ TO BE CONVERTED
    ├── Task.java                      # ⏳ Wave 3 (entity)
    ├── TaskActivity.java              # ⏳ Wave 6 (UI)
    ├── TaskListAdapter.java           # ⏳ Wave 6 (UI)
    ├── TaskDialogHelper.java          # ⏳ Wave 6 (UI)
    ├── TaskFilterManager.java         # ⏳ Wave 5 (logic)
    ├── TaskDatabaseHelper.java        # ⏳ Wave 4 (Room migration)
    └── TaskStatistics.java            # ⏳ Wave 5 (domain logic)
```

**Total:** 18 files (~3,907 lines Java → target: ~3,500 lines Kotlin)
**Converted:** 3 files (Wave 1) - 17% file count, 6% line count
**Remaining:** 15 Java files to convert in Waves 2-7

**Wave 1 Results (Complete ✅):**
- DatabaseConstants.java → DatabaseConstants.kt: 48 → 46 lines (-4%)
- AppLogger.java → AppLogger.kt: 87 → 122 lines (+40%, better docs)
- CompletionEntity.java → CompletionEntity.kt: 100 → 50 lines (-50%!)
- **Total:** 235 lines Java → 218 lines Kotlin (-7%)

---

## Purpose

This directory contains the **entire application** - now in **hybrid state** after Phase 4.5.2.

**Current State (Phase 4.5.3 Wave 1 Complete):**
- ✅ Gradle build system established (AGP 8.2.2, Kotlin 1.9.22)
- ✅ Project structure migrated to Gradle standard
- ✅ Wave 1 Kotlin conversions: 3 small utility files (DatabaseConstants, AppLogger, CompletionEntity)
- ✅ Build successfully compiles with hybrid Java/Kotlin codebase
- ⏳ Remaining 15 Java files to convert in Waves 2-7

**Phase 4.5.3 Achievements (Wave 1):**
- Set up Gradle 8.2 with Kotlin 1.9.22 and KSP
- Moved project to standard Gradle structure (app/src/main/)
- Converted 3 utility files to Kotlin with major boilerplate reduction
- Updated GitHub Actions to use Gradle build
- Build time: ~4 minutes (acceptable for CI/CD)

**What happens next (Waves 2-7):**
- Wave 2: HttpLogServer, SimpleHttpServer utilities
- Wave 3: Task, RecurrenceRule entities
- Wave 4: Room ORM migration (TaskDatabaseHelper → Repository + DAO)
- Wave 5: Business logic (TaskFilterManager, TaskStatistics → Use Cases)
- Wave 6: UI layer (MainActivity, TaskActivity → ViewModels + Activities)
- Wave 7: Update system (UpdateChecker, UpdateInstaller)

---

## Key Workflows

### 1. App Startup

**Entry Point:** `MainActivity.java:onCreate()`

**Flow:**
1. Initialize AppLogger singleton
2. Start HTTP log server (SimpleHttpServer on localhost:8080)
3. Display landing page with "Open Tasks" button
4. Settings menu provides "Check for Updates" and "View Logs"

**Code Reference:** `MainActivity.java:28-60`

**Related Docs:** [docs/LOGGING_SYSTEM.md](../../docs/LOGGING_SYSTEM.md), [docs/UPDATE_SYSTEM.md](../../docs/UPDATE_SYSTEM.md)

---

### 2. Task Management

**Entry Point:** `TaskActivity.java:onCreate()`

**Flow:**
1. Load tasks from database via TaskDatabaseHelper
2. Apply filters (TaskFilterManager)
3. Display in ListView (TaskListAdapter)
4. User actions:
   - Add → TaskDialogHelper.showAddDialog()
   - Complete → TaskDialogHelper.showCompletionDialog()
   - Edit → TaskDialogHelper.showEditDialog()
   - Delete → TaskDatabaseHelper.deleteTask()

**Code References:**
- Task CRUD: `TaskDatabaseHelper.java:100-450`
- Recurrence logic: `TaskDatabaseHelper.java:handleRecurringTaskCompletion()` (line ~600)
- Filtering: `TaskFilterManager.java:applyFilters()` (line ~50)

**Related Docs:** [docs/REFACTORING_BASELINE.md](../../docs/REFACTORING_BASELINE.md)

---

### 3. Task Completion (Recurrence Logic)

**Entry Point:** `TaskDatabaseHelper.java:completeTask(taskId)`

**Flow:**
1. Mark task as completed
2. Create completion record in `completions` table
3. If recurring task:
   - **INTERVAL type:** Reset task, advance due date (`resetIntervalTask()`)
   - **FREQUENCY type:** Increment counter, check period boundary (`incrementFrequencyProgress()`)
4. Update streaks (current streak, longest streak)

**Code References:**
- Complete logic: `TaskDatabaseHelper.java:450-550`
- INTERVAL reset: `TaskDatabaseHelper.java:resetIntervalTask()` (~line 600)
- FREQUENCY update: `TaskDatabaseHelper.java:incrementFrequencyProgress()` (~line 650)
- Streak calculation: `TaskStatistics.java:calculateStreak()` (~line 50)

**Critical:** This logic will be refactored into separate Use Cases and Services in Phase 4.5.4.

---

### 4. Logging System

**Entry Point:** `AppLogger.getInstance(context)`

**Flow:**
1. Singleton initialization on first call
2. All logging calls go to AppLogger
3. Logs stored in-memory (500 line circular buffer)
4. Parallel logging to Android Logcat
5. HTTP server exposes logs via localhost:8080/logs

**Code References:**
- AppLogger implementation: `AppLogger.java:15-87`
- HTTP server: `SimpleHttpServer.java:20-80`
- Integration: `MainActivity.java:50-60`

**Related Docs:** [docs/LOGGING_SYSTEM.md](../../docs/LOGGING_SYSTEM.md)

---

## Important Conventions

**Before Refactoring (Current):**
- **No architecture:** Everything in one package
- **God-Classes:** TaskDatabaseHelper (806 lines), TaskActivity (392 lines)
- **Mixed responsibilities:** UI calls database directly, business logic in Activities
- **No tests:** 0% test coverage

**After Refactoring (Phase 4.5):**
- **Clean Architecture:** Presentation → Domain → Data
- **Single Responsibility:** Max 200 lines per class
- **MVVM:** ViewModels orchestrate Use Cases
- **70%+ test coverage** for domain layer

---

## Database Schema

**Version:** 4
**Tables:** 2 (tasks, completions)

**tasks table (17 columns):**
- Basic: id, title, description, category, created_at, due_date, is_completed, priority
- Recurrence: recurrence_type, recurrence_amount, recurrence_unit, last_completed_date
- Period tracking: completions_this_period, current_period_start
- Streaks: current_streak, longest_streak, last_streak_date

**completions table (6 columns):**
- completion_id, task_id (FK), completed_at, time_spent, difficulty, notes

**Code Reference:** `DatabaseConstants.java:10-80` (schema constants), `TaskDatabaseHelper.java:30-70` (CREATE TABLE statements)

---

## Known Issues (Will Be Fixed in Phase 4.5)

1. **TaskDatabaseHelper is a God-Class** (806 lines)
   - Does CRUD + recurrence logic + streak tracking + statistics
   - Violates Single Responsibility Principle

2. **TaskActivity has too many responsibilities** (392 lines)
   - UI rendering + filtering + statistics + dialog management

3. **No separation of concerns**
   - Business logic mixed with UI and database code
   - No testability (tightly coupled to Android framework)

4. **Package name inconsistency**
   - Manifest says `com.secretary.helloworld`
   - Should be `com.secretary`

5. **No external libraries**
   - Can't use Room, WorkManager, LiveData
   - Limited by Termux build constraints

**Resolution:** Phase 4.5 refactoring will address all these issues.

---

## Testing

**Current:** NO automated tests (0% coverage)

**Why:** Code is tightly coupled to Android framework, difficult to test.

**Future (Phase 4.5.4+):**
- Domain layer: 70%+ unit test coverage
- Use Cases fully testable (no Android dependencies)
- Repository pattern enables mocking

**Manual Testing:**
- See [docs/REFACTORING_BASELINE.md](../../docs/REFACTORING_BASELINE.md) for test scenarios
- Use HTTP logs for debugging: `curl http://localhost:8080/logs`

---

## Migration Path (Phase 4.5)

**Phase 4.5.2: Package Structure**
- Move files to features/tasks/{presentation, domain, data}

**Phase 4.5.3: Data Layer**
- Migrate to Room ORM
- Create Repository implementations

**Phase 4.5.4: Domain Layer**
- Extract business logic into Use Cases
- Break down TaskDatabaseHelper into services

**Phase 4.5.5: Presentation Layer**
- Create ViewModels
- Remove business logic from Activities

**Phase 4.5.6: Testing**
- Write unit tests for Use Cases (70%+ coverage)
- Write integration tests for Repositories

---

## Related Documentation

- [Project CLAUDE.md](../../CLAUDE.md) - Architecture overview and standards
- [ROADMAP.md](../../ROADMAP.md) - Phase 4.5 refactoring plan
- [ARCHITECTURE_AUDIT.md](../../ARCHITECTURE_AUDIT.md) - Problems with current architecture
- [docs/LOGGING_SYSTEM.md](../../docs/LOGGING_SYSTEM.md) - HTTP logging implementation
- [docs/UPDATE_SYSTEM.md](../../docs/UPDATE_SYSTEM.md) - Auto-update mechanism
- [docs/REFACTORING_BASELINE.md](../../docs/REFACTORING_BASELINE.md) - System behavior baseline

---

**Last Updated:** 2025-11-13
**Status:** Phase 4.5.3 Wave 1 Complete - Kotlin migration in progress (14%)
**Version:** v0.3.28 (Build 328)
**Maintainer:** AI Secretary Development Team
