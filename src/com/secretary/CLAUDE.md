# src/com/secretary - Main Application Code (Pre-Refactoring)

**Purpose:** Contains all application code before Phase 4.5 architecture refactoring.

**Status:** Legacy structure - will be refactored in Phase 4.5.2+

**Key Constraint:** All code in single package `com.secretary.helloworld` (flat structure, no layers)

---

## Directory Structure

```
src/com/secretary/
├── MainActivity.java              # App entry point, Settings menu, HTTP server initialization
├── TaskActivity.java              # Main task management UI (list, filters, CRUD operations)
├── Task.java                      # Task entity with recurrence logic and getters/setters
├── TaskDatabaseHelper.java        # SQLite database manager (CRUD, recurrence, streaks) - 806 lines
├── TaskListAdapter.java           # ListView adapter for task list display
├── TaskDialogHelper.java          # Task creation/edit/completion dialogs
├── TaskFilterManager.java         # Search, filter, and sort logic for tasks
├── TaskStatistics.java            # Analytics calculations (completion stats, streaks)
├── DatabaseConstants.java         # Database schema constants (table/column names)
├── AppLogger.java                 # In-memory logging system (500 entry buffer)
├── SimpleHttpServer.java          # HTTP server on localhost:8080 for log access
├── UpdateChecker.java             # GitHub Releases API client for version checking
└── UpdateInstaller.java           # APK download and installation via DownloadManager
```

**Total:** 13 Java files, ~3,200 lines of code (before Phase 4.5.1 cleanup)

---

## Purpose

This directory contains the **entire application** in a flat structure. All code layers (UI, business logic, data access) are mixed in one package.

**Why it exists:**
- Rapid prototyping during Phase 0-4
- No separation of concerns (monolithic design)
- Will be refactored into Clean Architecture (Phase 4.5.2-4.5.5)

**What will happen (Phase 4.5):**
- Code will be split into features/tasks/{presentation, domain, data}
- God-classes will be broken down (e.g., TaskDatabaseHelper → Use Cases + Services)
- Business logic will be extracted from UI (TaskActivity → ViewModel + Use Cases)

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
**Status:** Legacy code - will be refactored in Phase 4.5
**Maintainer:** AI Secretary Development Team
