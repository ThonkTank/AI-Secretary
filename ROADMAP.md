# AI Secretary - Development Roadmap

**Current Version:** v0.3.40 (Build 340) - Kotlin Migration in Progress
**Last Updated:** 2025-11-14
**Status:** Phase 4.5.3 (Kotlin Migration + Gradle Setup) - Wave 1-9 Complete, Wave 10 (Incremental Refactor) IN PROGRESS - Steps 1-5 Complete (Room DAOs, TaskRepository, RecurrenceService, StreakService, CompletionRepository), Step 6 Starting (Cleanup)

**Update when**: Completing phases, adding TODOs, changing priorities, finishing major features.

**Critical Note:** Kotlin migration moved forward to Phase 4.5.3. After Java→Kotlin conversion and Gradle setup, Phase 4.5.4-4.5.5 (Room, Use Cases, MVVM) will be completed with proper tooling. Clean Architecture foundation (Phase 4.5.1-4.5.2) is complete.

---

## 📊 Current Status

### Active Development: Phase 4 - Motivation & Statistics 🚧

**Resumed:** 2025-11-13 (after Phase 4.5.1-4.5.2 completion)

**Completed (30%):**
- ✅ Streak Tracking (current and longest streaks)
- ✅ Database schema with streak fields
- ✅ Basic streak calculation logic

**In Progress:**
- 🚧 Visual motivation features (progress bars, badges)
- 🚧 Statistics display (daily/weekly completion counts)
- 🚧 Motivational messages

**Progress:** 30% complete - continuing where we left off

### Completed Refactoring: Phase 4.5 (Partial) ✅

**Why Partial?** Java limitations make Room/MVVM too complex. Deferred to Kotlin migration.

**Completed:**
- ✅ Phase 4.5.1: Critical Cleanup (13.4% codebase reduction, 496 lines deleted)
- ✅ Phase 4.5.2: Package Structure (Clean Architecture foundation established)

**Next Steps:**
- 🚧 Phase 4.5.3: Kotlin Migration + Gradle Setup (ACTIVE - moved forward)
- ⏳ Phase 4.5.4: Domain Layer - Use Cases & Services (after Kotlin)
- ⏳ Phase 4.5.5: Presentation Layer - MVVM (after Kotlin)

**Result:** Clean codebase + package structure ready for Kotlin migration NOW.

**After Phase 4.5:**
- ⏳ Phase 5: Intelligent Planning (4-5 weeks)
- ⏳ Phase 6: Widget & Polish (3-4 weeks)
- ⏳ Phase 7: Public Release Preparation

---

## 📋 Priority Definitions

Standard for all TODOs in this roadmap:

**CRITICAL:** Feature completely broken or unusable, blocks core functionality, prevents app launch or causes crashes
- Example: "Database migration fails causing app crash on update"

**HIGH:** Important feature missing or severely impaired, significantly impacts user workflow, confusing UX leading to frequent errors
- Example: "Streak tracking shows incorrect data"

**MEDIUM:** Feature incomplete but partially usable, suboptimal UX requiring too many steps, non-blocking bugs or inconsistencies
- Example: "Statistics panel needs better visualization"

**LOW:** Nice-to-have improvements, code refactoring or cleanup, small polish or consistency fixes
- Example: "Add tooltips to streak indicators"

---

## 🎯 Phase 4: Motivation & Statistics (Current)

**Goal:** User motivation through gamification and visual feedback

**Duration:** 2-3 weeks
**Progress:** 30% complete

### Active TODOs

**CRITICAL:**
- [ ] None currently

**HIGH:**
- [ ] Statistics calculation service - compute daily/weekly completion counts
  - Location: `src/com/secretary/TaskStatistics.java`
  - Dependencies: completions table, current date logic
- [ ] Statistics display in TaskActivity - show today/week stats above list
  - Location: `src/com/secretary/TaskActivity.java`
  - UI: Add TextView or custom view for stats panel

**MEDIUM:**
- [ ] Visual streak indicator - improve streak display beyond emoji
  - Current: "🔥 3" text
  - Goal: Progress bar or custom view showing current vs longest
- [ ] Completion rate visualization - percentage of tasks completed on time
  - Location: New StatisticsView widget
  - Data source: TaskStatistics calculations
- [ ] Motivational messages - encourage users based on streaks and completions
  - Location: TaskActivity or new MotivationHelper
  - Triggers: Opening app, completing tasks, achieving streaks

**LOW:**
- [ ] Streak history graph - line chart showing streak over time
  - Nice-to-have: Shows motivation trends
  - Requires: Chart library or custom drawing
- [ ] Achievement badges - milestone rewards for streaks/completions
  - Future: Gamification system
  - Currently: Low priority until core stats work

### Technical Details

**What exists:**
- `Task.java:37-39` - Streak fields (currentStreak, longestStreak, lastStreakDate)
- `TaskDatabaseHelper.java` - Streak update logic in completeTask()
- `TaskStatistics.java:20+` - Basic statistics framework
- `completions` table - Historical completion data

**What's needed:**
- Statistics aggregation queries (daily, weekly, all-time)
- UI components for displaying stats
- Motivational message system
- Better visual indicators for streaks

**Testing:**
- Manual: Create tasks, complete them multiple days, verify streak increments
- Manual: Check statistics panel shows correct counts
- Manual: Test motivational messages appear at right times

---

## ✅ Completed Phases

### Phase 0: Foundation Systems (100%)
**Auto-update via GitHub Releases + HTTP logging on localhost:8080**
- UpdateChecker/UpdateInstaller for seamless updates
- AppLogger + SimpleHttpServer for development logging
- Fully functional CI/CD pipeline

### Phase 1: Taskmaster Foundation (100%)
**Database, UI, and CRUD operations**
- Task entity with 17 columns across 2 tables
- SQLite with migrations (v1→v4)
- TaskActivity with full task management UI
- Priorities, categories, due dates

### Phase 2: Core Task Management (100%)
**Recurrence system and advanced features**
- Two recurrence types: INTERVAL ("Every X days"), FREQUENCY ("X times per week")
- Smart completion logic with automatic task reset
- Task editing, search, filtering by status/priority/category
- Sort by 5 criteria (priority, due date, category, created date, title)

### Phase 3: Tracking & Analytics (100%)
**Completion history and data collection**
- completions table (6 columns) for historical tracking
- TaskStatistics class for analytics
- Completion dialog with time spent, difficulty, notes
- Average time calculation from history

---

## 🏗️ Phase 4.5: Architecture Refactor (Upcoming)

**Goal:** Complete architecture overhaul - from flat structure to Clean Architecture with feature modules

**Duration:** 3-4 weeks (13-18 working days)
**Progress:** 0% complete
**When:** After Phase 4 completion, before Phase 5
**Criticality:** 🔴 HIGH - Technical debt is blocking scalability

**Based on:** ARCHITECTURE_AUDIT.md findings - addresses critical issues:
- Logging redundancy (5 files → 2, save 469 lines)
- God-Classes (TaskDatabaseHelper 806 lines → modular)
- No tests (0% → 70% coverage)
- Mixed responsibilities (separation of concerns)
- No modern patterns (MVVM, Repository, Use Cases)

---

### Target Architecture: Hybrid Feature + Clean Layers

**Combines:** Feature-based modules (Salt Marcher) + Clean Architecture layers (Android best practices)

```
AI-Secretary-latest/
├── src/com/secretary/
│   ├── app/                           # App entry point
│   │   └── MainActivity.java
│   │
│   ├── core/                          # Shared foundations
│   │   ├── logging/
│   │   │   ├── AppLogger.java         # Core logger (114 lines) ✅
│   │   │   └── HttpLogServer.java     # Consolidated HTTP server
│   │   ├── network/
│   │   │   ├── UpdateChecker.java
│   │   │   └── UpdateInstaller.java
│   │   └── di/
│   │       └── AppModule.java         # Dependency injection
│   │
│   ├── features/                      # Feature modules
│   │   ├── tasks/
│   │   │   ├── data/
│   │   │   │   ├── TaskDao.java       # Room DAO
│   │   │   │   ├── TaskEntity.java    # DB entity
│   │   │   │   └── TaskRepositoryImpl.java
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Task.java      # Pure domain model
│   │   │   │   │   └── RecurrenceRule.java
│   │   │   │   ├── repository/
│   │   │   │   │   └── TaskRepository.java  # Interface
│   │   │   │   ├── usecase/
│   │   │   │   │   ├── CompleteTaskUseCase.java
│   │   │   │   │   ├── CreateTaskUseCase.java
│   │   │   │   │   └── UpdateTaskUseCase.java
│   │   │   │   └── service/
│   │   │   │       ├── RecurrenceService.java
│   │   │   │       └── StreakService.java
│   │   │   └── presentation/
│   │   │       ├── TaskActivity.java
│   │   │       ├── TaskViewModel.java
│   │   │       ├── TaskListAdapter.java
│   │   │       └── dialog/
│   │   │           ├── AddTaskDialog.java
│   │   │           ├── EditTaskDialog.java
│   │   │           └── CompletionDialog.java
│   │   │
│   │   └── statistics/
│   │       ├── data/
│   │       │   ├── CompletionDao.java
│   │       │   └── StatisticsRepositoryImpl.java
│   │       ├── domain/
│   │       │   ├── model/
│   │       │   │   └── TaskStatistics.java
│   │       │   ├── repository/
│   │       │   │   └── StatisticsRepository.java
│   │       │   └── usecase/
│   │       │       └── CalculateStreakUseCase.java
│   │       └── presentation/
│   │           └── StatisticsViewModel.java
│   │
│   └── shared/
│       ├── database/
│       │   ├── TaskDatabase.java      # Room Database
│       │   └── Migrations.java
│       └── util/
│           └── DateUtils.java
│
├── devkit/                            # Development tools
│   ├── build/
│   │   ├── build.sh
│   │   └── build-current.sh
│   ├── testing/
│   │   ├── fixtures/                  # Test data
│   │   └── utils/                     # Test helpers
│   └── utilities/
│       ├── log_access.sh
│       └── version_bump.sh
│
├── docs/
│   ├── ARCHITECTURE.md                # Architecture decisions (new)
│   ├── DEBUGGING.md                   # Debug workflows (new)
│   ├── LOGGING_SYSTEM.md
│   └── UPDATE_SYSTEM.md
│
└── [res/, .github/, CLAUDE.md, README.md, ROADMAP.md]
```

**Key Principles:**
- **Feature Modules:** Self-contained by domain (tasks, statistics)
- **Clean Layers per Feature:** data → domain → presentation
- **Core for Shared:** Logging, networking, DI used across features
- **Strict Dependencies:** Presentation → Domain → Data (never reverse)

---

## Phase 4.5.1: Critical Cleanup ✅ COMPLETE

**Goal:** Remove redundant code and prepare for refactoring
**When:** BEFORE any restructuring
**Why:** Reduces codebase by 13% (469 lines), simplifies migration
**Status:** ✅ COMPLETED 2025-11-13
**Actual Time:** ~2 hours

### Completed TODOs

**CRITICAL:**
- [x] Delete redundant logging files ✅
  - GOAL: Eliminate 60% of logging code (5 files → 2)
  - Location: `src/com/secretary/`
  - Action: Deleted `LogServer.java` (148 lines), `LogProvider.java` (110 lines), `NanoHTTPD.java` (211 lines)
  - Einsparung: 469 Zeilen (13% der Codebase)
  - Result: Only AppLogger.java and SimpleHttpServer.java remain
- [x] Fix AppLogger inconsistency ✅
  - GOAL: True in-memory logging (remove file writing)
  - Location: `src/com/secretary/AppLogger.java:86-98`
  - Action: Removed `logFile` variable and `writeToFile()` method
  - Result: AppLogger is now 87 lines (from 114 lines), truly IN-MEMORY
- [x] Verify dead code removal ✅
  - GOAL: Ensure LogProvider is truly unused
  - Action: Checked AndroidManifest.xml for ContentProvider declaration
  - Result: Removed ContentProvider entry from manifest (lines 34-38)

**HIGH:**
- [x] Create refactoring branch ✅
  - GOAL: Isolate refactoring work from main development
  - Action: `git checkout -b refactoring/phase-4.5-architecture`
  - Result: Branch created successfully
- [x] Create test infrastructure structure ✅
  - GOAL: Prepare for testing in later phases
  - Location: `devkit/testing/`
  - Action: Created directory structure (domain/, data/, integration/, fixtures/)
  - Result: Test directories ready, README.md created
  - Note: Actual test implementation deferred to Phase 4.5.4-4.5.6

**MEDIUM:**
- [x] Document current system behavior ✅
  - GOAL: Baseline for regression testing
  - Action: Created `docs/REFACTORING_BASELINE.md`
  - Content: 10 critical user flows, database schema, test scenarios
  - Result: Complete baseline documentation for regression validation

### Technical Details

**What gets deleted:**
```
❌ LogServer.java         (148 lines) - Duplicate HTTP server using NanoHTTPD
❌ LogProvider.java       (110 lines) - Unused ContentProvider
❌ NanoHTTPD.java         (211 lines) - Overkill library for simple logging
```

**What gets fixed:**
```java
// AppLogger.java - BEFORE
private File logFile;
private void writeToFile() {
    // Writes to AISecretary_logs.txt (inconsistent with "IN-MEMORY")
}

// AppLogger.java - AFTER
// logFile removed
// writeToFile() removed
// Pure in-memory logging (500 lines max)
```

**Testing setup:**
```bash
# Add to build dependencies (GitHub Actions)
- JUnit 5: junit:junit:4.13.2
- Mockito: mockito-core:5.x
```

**Deliverables:** ✅ ALL COMPLETE
- ✅ 496 lines deleted total (469 from logging files + 27 from AppLogger fix)
- ✅ AppLogger fixed (87 lines, truly in-memory)
- ✅ Test infrastructure structure created (devkit/testing/)
- ✅ Refactoring branch created (refactoring/phase-4.5-architecture)
- ✅ Baseline documentation (docs/REFACTORING_BASELINE.md)
- ✅ ContentProvider removed from AndroidManifest.xml

**Actual Savings:**
- Lines deleted: 496 (13.4% of 3,712 line codebase)
- Files deleted: 3 (LogServer.java, LogProvider.java, NanoHTTPD.java)
- Logging system: 5 files → 2 files (60% reduction)

**Estimated time:** 1-2 days
**Actual time:** ~2 hours

---

## Phase 4.5.2: Package Structure ✅ COMPLETE

**Goal:** Create new directory structure and migrate files
**When:** After cleanup
**Why:** Foundation for architecture - enables separation of concerns
**Status:** ✅ COMPLETED 2025-11-13
**Actual Time:** ~3 hours

### Completed TODOs

**HIGH:**
- [x] Create core/ directory structure ✅
  - Created: `src/com/secretary/core/{logging,network}/`
  - Created: `src/com/secretary/features/{tasks,statistics}/{data,domain,presentation}/`
  - Created: `src/com/secretary/shared/{database,util}/`
  - Created: `src/com/secretary/app/`
- [x] Move core files ✅
  - `AppLogger.java` → `core/logging/AppLogger.java`
  - `SimpleHttpServer.java` → `core/logging/HttpLogServer.java` (RENAMED)
  - `UpdateChecker.java` → `core/network/UpdateChecker.java`
  - `UpdateInstaller.java` → `core/network/UpdateInstaller.java`
  - `DatabaseConstants.java` → `shared/database/DatabaseConstants.java`
  - `MainActivity.java` → `app/MainActivity.java`
  - Package declarations updated for all files
  - Import statements fixed in all remaining files
- [x] Update GitHub Actions workflow ✅
  - Updated javac file paths for new structure
  - Changed d8 to use find for all .class files
  - Enabled workflow for refactoring branch (temporary)
  - Build verified: SUCCESS ✅

**MEDIUM:**
- [x] Move build.sh to devkit/ ✅
  - `build.sh` → `devkit/build/build.sh`
  - Updated script header with documentation
  - Updated paths to work from project root
  - Deleted obsolete `build-current.sh`
- [x] Move MainActivity to app/ ✅
  - Moved with all import updates
  - AndroidManifest.xml updated: `.app.MainActivity`

### File Migration Map

**Phase 4.5.2 moves (8 files):**
```
src/com/secretary/
├── AppLogger.java          → core/logging/AppLogger.java
├── SimpleHttpServer.java   → core/logging/HttpLogServer.java ⚠️ RENAME
├── UpdateChecker.java      → core/network/UpdateChecker.java
├── UpdateInstaller.java    → core/network/UpdateInstaller.java
├── MainActivity.java       → app/MainActivity.java
└── DatabaseConstants.java  → shared/database/DatabaseConstants.java

Root:
├── build.sh                → devkit/build/build.sh
└── build-current.sh        → devkit/build/build-current.sh
```

**Remaining files (to be moved in later phases):**
```
src/com/secretary/
├── Task.java                        # → Phase 4.5.4 (Domain)
├── TaskActivity.java                # → Phase 4.5.5 (Presentation)
├── TaskListAdapter.java             # → Phase 4.5.5 (Presentation)
├── TaskDialogHelper.java            # → Phase 4.5.5 (Presentation)
├── TaskFilterManager.java           # → Phase 4.5.5 (Presentation)
├── TaskDatabaseHelper.java          # → Phase 4.5.3 (Data - refactor to Room)
└── TaskStatistics.java              # → Phase 4.5.4 (Domain)
```

### Technical Details

**Package declarations update:**
```java
// BEFORE
package com.secretary.helloworld;

// AFTER
package com.secretary.helloworld.core.logging;
package com.secretary.helloworld.core.network;
package com.secretary.helloworld.app;
```

**Import updates (example):**
```java
// MainActivity.java
// BEFORE
import com.secretary.helloworld.AppLogger;
import com.secretary.helloworld.SimpleHttpServer;

// AFTER
import com.secretary.helloworld.core.logging.AppLogger;
import com.secretary.helloworld.core.logging.HttpLogServer;
```

**GitHub Actions update:**
```yaml
# .github/workflows/build-and-release.yml
javac -source 8 -target 8 \
  -d build/classes \
  -cp $ANDROID_SDK_ROOT/platforms/android-33/android.jar \
  src/com/secretary/app/*.java \
  src/com/secretary/core/logging/*.java \
  src/com/secretary/core/network/*.java \
  src/com/secretary/shared/database/*.java
```

**Deliverables:** ✅ ALL COMPLETE
- ✅ Clean Architecture directory structure created
  - core/ (logging, network)
  - features/ (tasks, statistics with data/domain/presentation)
  - shared/ (database, util)
  - app/ (MainActivity)
- ✅ 6 files migrated to new packages with updated imports
- ✅ SimpleHttpServer renamed to HttpLogServer
- ✅ build.sh moved to devkit/build/ with updated documentation
- ✅ GitHub Actions workflow updated for new structure
- ✅ AndroidManifest.xml updated for MainActivity path
- ✅ All import statements fixed in remaining files (7 files)
- ✅ Build verified: SUCCESS on GitHub Actions ✅
- ✅ Foundation for Clean Architecture established

**Estimated time:** 2-3 days
**Actual time:** ~3 hours (faster than expected)

---

## Phase 4.5.3: Kotlin Migration + Gradle Setup (5-7 days)

**Goal:** Migrate entire Java codebase to Kotlin and establish Gradle build system
**When:** NOW - Moved forward from Phase 7 (after Phase 4.5.1-4.5.2 completion)
**Why:** Room, MVVM, and modern Android tools require Gradle. Doing this now avoids building temporary Java structures that would be obsolete in days.

### Overview

**Decision Rationale:**
- Attempting Room with Java + javac = dependency hell (transitive dependencies nightmare)
- Small codebase (18 files, 3,907 lines) = perfect migration window
- Clean Architecture foundation (Phase 4.5.1-4.5.2) complete = ready for migration
- Postponing makes migration harder (more code to convert later)
- Kotlin + Gradle unlocks: Room with KSP, Hilt, Coroutines, modern architecture

**Migration Approach:**
- **Incremental file-by-file conversion** (not "big bang" rewrite)
- **Small files first** (DatabaseConstants, AppLogger) → easier testing
- **Test after each conversion** → catch issues early
- **Preserve existing behavior** → no feature changes during migration
- **Gradle build on GitHub Actions** → Termux limitation workaround

### Active TODOs

**CRITICAL:**
- [x] Setup Gradle build configuration ✅ COMPLETE (2025-11-13)
  - GOAL: Replace manual aapt2/javac/d8 build with Gradle
  - Created: `build.gradle.kts`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
  - Configuration: AGP 8.2.2, Kotlin 1.9.22, compileSdk 35
  - Result: Gradle wrapper functional, build time ~4min
- [x] Configure GitHub Actions for Gradle ✅ COMPLETE (2025-11-13)
  - Updated: `.github/workflows/build-and-release.yml`
  - Changes: JDK 21 setup, gradle/actions/setup-gradle@v3, assembleRelease
  - Result: Automated builds working, v0.3.28 released successfully
- [x] Move source to Gradle structure ✅ COMPLETE (2025-11-13)
  - Moved: `src/` → `app/src/main/java/`
  - Moved: `res/` → `app/src/main/res/`
  - Moved: `AndroidManifest.xml` → `app/src/main/AndroidManifest.xml`
  - Result: Standard Gradle Android project layout

**HIGH:**
- [x] Convert small utility files first (Wave 1: 3 files, ~235 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: Practice conversion on simple files
  - Completed:
    1. `DatabaseConstants.java` (48 lines) → `DatabaseConstants.kt` (46 lines) ✅
       - object with const val (idiomatic Kotlin)
    2. `AppLogger.java` (87 lines) → `AppLogger.kt` (122 lines) ✅
       - Singleton object pattern, @JvmStatic for Java interop
    3. `CompletionEntity.java` (100 lines) → `CompletionEntity.kt` (50 lines) ✅
       - data class with Room annotations (-50% lines!)
  - Result: 235 lines Java → 218 lines Kotlin (-7%), successful build
- [x] Convert domain models (Wave 2: 2 files, ~453 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: Core data structures to Kotlin
  - Completed:
    1. `Task.java` (297 lines) → `Task.kt` (166 lines) ✅
       - Data class with nested objects for constants
       - Temporarily mutable (var) for Java interop (Wave 5 will fix)
       - Backward-compatible constants for Java switch statements
       - -44% line reduction
    2. `TaskStatistics.java` (156 lines) → `TaskStatistics.kt` (139 lines) ✅
       - Cleaner with use{} for cursor management
       - -11% line reduction
  - Result: 453 lines Java → 305 lines Kotlin (-33%)
  - Challenges: Java interop issues (setters, constants), all resolved
- [x] Convert logging system (Wave 3: 1 file, ~145 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: Core infrastructure to Kotlin
  - Completed:
    1. `HttpLogServer.java` (145 lines) → `HttpLogServer.kt` (153 lines) ✅
       - use{} for resource management
       - Kotlin nullable types (ServerSocket?)
       - when expressions for routing
       - String templates for responses
       - +5% lines (better clarity)
  - Result: 145 lines Java → 153 lines Kotlin (+5%)
  - Test: Verified logs accessible via curl, server working
- [x] Convert update system (Wave 4: 2 files, ~274 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: Auto-update system to Kotlin
  - Completed:
    1. `UpdateChecker.java` (127 lines) → `UpdateChecker.kt` (117 lines) ✅
       - Nullable types for error handling
       - String templates for version comparison
       - -8% line reduction
    2. `UpdateInstaller.java` (147 lines) → `UpdateInstaller.kt` (139 lines) ✅
       - BroadcastReceiver as object expression
       - Smart casts for Intent extras
       - -5% line reduction
  - Result: 274 lines Java → 256 lines Kotlin (-7%)
  - Test: Update check verified working
- [x] Convert Room reference entities (Wave 5: 3 files, ~464 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: Room entities in Kotlin (will be used in Phase 4.5.4)
  - Completed:
    1. `TaskEntity.java` (211 lines) → `TaskEntity.kt` (132 lines) ✅
       - Data class with Room annotations
       - -37% line reduction
    2. `TaskDao.java` (153 lines) → `TaskDao.kt` (68 lines) ✅
       - Interface with suspend functions
       - -56% line reduction!
    3. `TaskDatabase.java` (100 lines) → `TaskDatabase.kt` (57 lines) ✅
       - Abstract class with companion object
       - -43% line reduction
  - Result: 464 lines Java → 257 lines Kotlin (-45%)
  - Note: These are reference implementations, will be refined in Phase 4.5.4
- [x] Convert UI helper classes (Wave 6: 3 files, ~545 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: UI helper utilities to Kotlin
  - Completed:
    1. `TaskFilterManager.java` (205 lines) → `TaskFilterManager.kt` (152 lines) ✅
       - Functional filtering with filter chains
       - Enum classes with constructor parameters
       - -26% line reduction
    2. `TaskListAdapter.java` (172 lines) → `TaskListAdapter.kt` (212 lines) ✅
       - ViewHolder pattern with lambda expressions
       - String templates for info display
       - +23% lines (better clarity)
    3. `TaskDialogHelper.java` (368 lines) → `TaskDialogHelper.kt` (404 lines) ✅
       - Lambda expressions for all listeners
       - Object expression for SeekBarChangeListener
       - Higher-order function for date picker
       - +10% lines (better clarity)
  - Result: 745 lines Java → 768 lines Kotlin (+3%, improved readability)
- [x] Convert main activity (Wave 7: 1 file, ~277 lines) ✅ COMPLETE (2025-11-13)
  - GOAL: App entry point to Kotlin
  - Completed:
    1. `MainActivity.java` (277 lines) → `MainActivity.kt` (282 lines) ✅
       - lateinit var for logger, nullable var for httpServer
       - String templates and when expressions
       - Safe call operators and scope functions
       - +2% lines (better clarity)
  - Result: 277 lines Java → 282 lines Kotlin (+2%)
  - Test: App launches correctly, HTTP server works
- [x] Convert legacy build script (Wave 8: 1 file, ~80 lines) ✅ COMPLETE (2025-11-14)
  - GOAL: Migrate build.sh to Kotlin build script
  - Completed:
    1. `build.sh` (shell script) → `build.gradle.kts` integration ✅
       - All builds now use Gradle exclusively
       - Local testing via `./gradlew assembleDebug`
  - Result: Unified build system
- [x] Convert task activity (Wave 9: 1 file, ~393 lines) ✅ COMPLETE (2025-11-14)
  - GOAL: Main task management UI to Kotlin
  - Completed:
    1. `TaskActivity.java` (393 lines) → `TaskActivity.kt` (385 lines) ✅
       - lateinit var for views and dependencies
       - Object expressions for listener interfaces
       - Functional filter and sort operations
       - -2% lines (Kotlin concision)
  - Result: 393 lines Java → 385 lines Kotlin (-2%)
  - Test: All task operations work correctly (v0.3.36 Build 336)
- [ ] Convert database helper with incremental refactor (Wave 10: 1 file → 6+ files, ~808 lines) ⏳ IN PROGRESS
  - GOAL: Migrate TaskDatabaseHelper to Kotlin + Clean Architecture (combine Kotlin migration with refactoring)
  - STRATEGY: Incremental extraction into separate Kotlin files (avoid creating temporary God-Class)
  - Approach:
    **Step 1: Room Database Setup**
    - Create `data/database/AppDatabase.kt` - Room database class
    - Create `data/database/TaskEntity.kt` - @Entity with 17 fields
    - Create `data/database/TaskDao.kt` - @Dao with CRUD methods
    - Create `data/database/Migrations.kt` - Migration v5 → Room v6
    - Test: Database initializes, migrations work

    **Step 2: Task CRUD → TaskRepository**
    - Create `domain/repository/TaskRepository.kt` - Interface
    - Create `data/repository/TaskRepositoryImpl.kt` - Implementation with TaskDao
    - Mapping: TaskEntity ↔ Task (domain model)
    - Update TaskActivity to use TaskRepository
    - Test: Task list loads, add/edit/delete work

    **Step 3: Recurrence Logic → RecurrenceService**
    - Create `domain/service/RecurrenceService.kt` - All recurrence methods
    - Extract: handleRecurringTaskCompletion, calculateNextDueDate, etc.
    - Test: INTERVAL and FREQUENCY tasks work correctly

    **Step 4: Streak Management → StreakService**
    - Create `domain/service/StreakService.kt` - updateStreak logic
    - Test: Streak updates on task completion

    **Step 5: Completion Tracking → CompletionRepository**
    - Create `domain/repository/CompletionRepository.kt` - Interface
    - Create `data/repository/CompletionRepositoryImpl.kt` - Implementation
    - Create `data/database/CompletionDao.kt` - Room DAO
    - Test: Completion history works

    **Step 6: Cleanup**
    - Delete `TaskDatabaseHelper.java` (fully replaced)
    - Update TaskStatistics.kt to use Repository
    - Final integration testing
  - Result: 808 lines Java → ~6 focused Kotlin files with Clean Architecture
  - Test: All database operations work, no regressions

**MEDIUM:**
- [ ] Add Kotlin dependencies
  - Location: `app/build.gradle.kts`
  - Dependencies:
    ```kotlin
    dependencies {
        // Kotlin
        implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")

        // Coroutines
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

        // AndroidX Core
        implementation("androidx.core:core-ktx:1.12.0")
        implementation("androidx.appcompat:appcompat:1.6.1")

        // Room (for Phase 4.5.4)
        implementation("androidx.room:room-runtime:2.6.1")
        implementation("androidx.room:room-ktx:2.6.1")
        ksp("androidx.room:room-compiler:2.6.1")

        // Testing
        testImplementation("junit:junit:4.13.2")
        testImplementation("org.mockito:mockito-core:5.7.0")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    }
    ```
- [ ] Setup KSP for Room annotation processing
  - GOAL: Modern annotation processing (2x faster than KAPT)
  - Location: `app/build.gradle.kts`
  - Plugin: `id("com.google.devtools.ksp") version "1.9.20-1.0.14"`
  - Note: KSP version must match Kotlin version (1.9.20)
- [ ] Configure proguard rules for Kotlin
  - Location: `app/proguard-rules.pro`
  - Rules: Keep Kotlin reflection, Room annotations
- [ ] Update documentation
  - GOAL: Reflect Kotlin in all docs
  - Files to update:
    - `CLAUDE.md` - Build process, architecture
    - `README.md` - Tech stack
    - `docs/LOGGING_SYSTEM.md` - Kotlin examples
    - `docs/UPDATE_SYSTEM.md` - Kotlin examples

### Technical Details

**Gradle configuration (build.gradle.kts):**
```kotlin
plugins {
    id("com.android.application") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.20"
    id("com.google.devtools.ksp") version "1.9.20-1.0.14"
}

android {
    namespace = "com.secretary.helloworld"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.secretary.helloworld"
        minSdk = 28
        targetSdk = 35
        versionCode = 327
        versionName = "0.3.27"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=kotlin.RequiresOptIn"
        )
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.20")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
```

**GitHub Actions workflow update:**
```yaml
name: Build and Release APK

on:
  push:
    branches: [ main, refactoring/phase-4.5-architecture ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up JDK 21
      uses: actions/setup-java@v4
      with:
        java-version: '21'
        distribution: 'temurin'

    - name: Setup Gradle
      uses: gradle/actions/setup-gradle@v3
      with:
        gradle-version: wrapper

    - name: Grant execute permission for gradlew
      run: chmod +x ./gradlew

    - name: Build APK with Gradle
      run: ./gradlew assembleRelease

    - name: Sign APK
      run: |
        echo "${{ secrets.KEYSTORE_BASE64 }}" | base64 -d > release.keystore

        $ANDROID_HOME/build-tools/35.0.0/apksigner sign \
          --ks release.keystore \
          --ks-key-alias release \
          --ks-pass pass:${{ secrets.KEYSTORE_PASSWORD }} \
          --key-pass pass:${{ secrets.KEYSTORE_PASSWORD }} \
          --out AISecretary-signed.apk \
          app/build/outputs/apk/release/app-release-unsigned.apk

    - name: Extract version
      id: version
      run: |
        VERSION=$(grep 'versionName' app/build.gradle.kts | sed 's/.*"\(.*\)".*/\1/')
        echo "version=$VERSION" >> $GITHUB_OUTPUT

    - name: Create Release
      uses: softprops/action-gh-release@v1
      with:
        tag_name: v${{ steps.version.outputs.version }}
        name: AI Secretary v${{ steps.version.outputs.version }}
        body: |
          Automated build of AI Secretary (Kotlin)

          Version: ${{ steps.version.outputs.version }}
          Built with Gradle + Kotlin
        files: AISecretary-signed.apk
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

**Kotlin conversion example (DatabaseConstants):**
```kotlin
// BEFORE (Java)
public class DatabaseConstants {
    public static final String DATABASE_NAME = "task_database.db";
    public static final int DATABASE_VERSION = 4;

    public static final String TABLE_TASKS = "tasks";
    public static final String COLUMN_ID = "id";
    public static final String COLUMN_TITLE = "title";
}

// AFTER (Kotlin - idiomatic)
object DatabaseConstants {
    const val DATABASE_NAME = "task_database.db"
    const val DATABASE_VERSION = 4

    const val TABLE_TASKS = "tasks"
    const val COLUMN_ID = "id"
    const val COLUMN_TITLE = "title"
}
```

**Kotlin conversion example (Task entity):**
```kotlin
// BEFORE (Java)
public class Task {
    private long id;
    private String title;
    private String description;
    private boolean isCompleted;

    public Task(long id, String title, String description, boolean isCompleted) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.isCompleted = isCompleted;
    }

    // 20+ getters and setters...

    public Task copy() {
        return new Task(id, title, description, isCompleted);
    }
}

// AFTER (Kotlin - data class)
data class Task(
    val id: Long = 0,
    val title: String,
    val description: String? = null,
    val isCompleted: Boolean = false,
    val category: String? = null,
    val priority: Int = 0,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val recurrenceType: RecurrenceType? = null,
    val recurrenceAmount: Int? = null,
    val recurrenceUnit: RecurrenceUnit? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0
) {
    // copy() method is FREE with data class!
    // equals(), hashCode(), toString() also free!
}

sealed class RecurrenceType {
    object INTERVAL : RecurrenceType()
    object FREQUENCY : RecurrenceType()
}
```

**Room with Kotlin + KSP:**
```kotlin
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,

    // ... 17 fields total
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY is_completed ASC, priority DESC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): TaskEntity?

    @Insert
    suspend fun insertTask(task: TaskEntity): Long

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)
}

@Database(entities = [TaskEntity::class, CompletionEntity::class], version = 5)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun completionDao(): CompletionDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_database"
                )
                .addMigrations(MIGRATION_4_5)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Schema compatible, no changes needed
            }
        }
    }
}
```

### Conversion Order & File Map

**Wave 1: Utilities (3 files, 235 lines)**
```
src/com/secretary/shared/database/DatabaseConstants.java (48 lines)
  → app/src/main/java/com/secretary/helloworld/shared/database/DatabaseConstants.kt

src/com/secretary/core/logging/AppLogger.java (87 lines)
  → app/src/main/java/com/secretary/helloworld/core/logging/AppLogger.kt

src/com/secretary/features/statistics/data/CompletionEntity.java (100 lines)
  → app/src/main/java/com/secretary/helloworld/features/statistics/data/CompletionEntity.kt
```

**Wave 2: Domain Models (2 files, 397 lines)**
```
src/com/secretary/Task.java (297 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/domain/model/Task.kt

src/com/secretary/TaskStatistics.java (100 lines)
  → app/src/main/java/com/secretary/helloworld/features/statistics/domain/model/TaskStatistics.kt
```

**Wave 3: Logging System (2 files, 232 lines)**
```
src/com/secretary/core/logging/HttpLogServer.java (145 lines)
  → app/src/main/java/com/secretary/helloworld/core/logging/HttpLogServer.kt

Updated AppLogger.kt integration
```

**Wave 4: Update System (2 files, 274 lines)**
```
src/com/secretary/core/network/UpdateChecker.java (127 lines)
  → app/src/main/java/com/secretary/helloworld/core/network/UpdateChecker.kt

src/com/secretary/core/network/UpdateInstaller.java (147 lines)
  → app/src/main/java/com/secretary/helloworld/core/network/UpdateInstaller.kt
```

**Wave 5: Database Layer (1→3 files, 806 lines)**
```
src/com/secretary/TaskDatabaseHelper.java (806 lines)
  → Split into:
    - TaskDatabaseHelper.kt (~200 lines) - Core operations
    - RecurrenceHelper.kt (~200 lines) - Recurrence logic
    - StreakHelper.kt (~100 lines) - Streak calculations
    - Remaining ~300 lines refactored/removed
```

**Wave 6: UI Layer (5 files, 1,490 lines)**
```
src/com/secretary/app/MainActivity.java (277 lines)
  → app/src/main/java/com/secretary/helloworld/app/MainActivity.kt

src/com/secretary/TaskFilterManager.java (205 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/presentation/TaskFilterManager.kt

src/com/secretary/TaskListAdapter.java (172 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/presentation/TaskListAdapter.kt

src/com/secretary/TaskDialogHelper.java (368 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/presentation/dialog/TaskDialogHelper.kt

src/com/secretary/TaskActivity.java (393 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/presentation/TaskActivity.kt
    (Consider splitting into multiple files)
```

**Wave 7: Room Reference (3 files, 464 lines)**
```
src/com/secretary/features/tasks/data/TaskEntity.java (211 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/data/TaskEntity.kt

src/com/secretary/features/tasks/data/TaskDao.java (153 lines)
  → app/src/main/java/com/secretary/helloworld/features/tasks/data/TaskDao.kt

src/com/secretary/shared/database/TaskDatabase.java (100 lines)
  → app/src/main/java/com/secretary/helloworld/shared/database/TaskDatabase.kt
```

**Total: 18 files, 3,907 lines → ~20 Kotlin files, ~3,500 lines** (20% reduction expected with Kotlin's conciseness)

### Testing Strategy

**After Each Wave:**
1. Build APK with Gradle on GitHub Actions
2. Install on device and test functionality
3. Read logs via `curl http://localhost:8080/logs`
4. Verify no regressions in baseline behavior (see `docs/REFACTORING_BASELINE.md`)

**Critical Test Cases (All Waves):**
- Task CRUD operations (create, read, update, delete)
- Task completion with recurrence (INTERVAL and FREQUENCY)
- Streak calculation and updates
- Search and filtering
- Statistics display
- Update check functionality
- HTTP log server accessibility

**Automated Tests (After conversion complete):**
- Unit tests for domain models (Task, TaskStatistics)
- Unit tests for services (RecurrenceHelper, StreakHelper)
- Integration tests for database operations
- UI tests for critical flows (task creation, completion)

### Deliverables

**Phase 4.5.3 Complete When:**
- ✅ Gradle build system configured and working on GitHub Actions
- ✅ All 18 Java files converted to Kotlin (~20 Kotlin files)
- ✅ Project structure matches Gradle standard (`app/src/main/java/`)
- ✅ All functionality working (no regressions)
- ✅ Logs accessible via HTTP server
- ✅ KSP configured for Room annotation processing
- ✅ Documentation updated (CLAUDE.md, README.md, docs/)
- ✅ Build time: <2 minutes on GitHub Actions
- ✅ Codebase ready for Room migration (Phase 4.5.4)

**Artifacts:**
- `build.gradle.kts` (project and app module)
- `settings.gradle.kts`
- `gradle.properties`
- `app/src/main/` directory structure
- Updated GitHub Actions workflow
- All Kotlin source files
- Updated documentation

**Success Criteria:**
- App launches without crashes
- All features work as before migration
- Logs show no errors during normal operations
- GitHub Actions build succeeds
- APK size similar or smaller than Java version
- Ready for Room + Use Cases + MVVM (Phase 4.5.4-4.5.5)

**Estimated time:** 5-7 days
- Gradle setup: 1 day
- Wave 1-2 (utilities, models): 1 day
- Wave 3-4 (logging, updates): 1 day
- Wave 5 (database): 2 days
- Wave 6 (UI): 2 days
- Wave 7 (Room reference): 0.5 days
- Testing & documentation: 0.5 days

**Risk Mitigation:**
- Incremental approach allows reverting to last working state
- Each wave tested independently
- GitHub Actions ensures build always works
- Existing Java code kept until Kotlin version verified
- No deadline pressure (personal project, no users)

### Progress Update (2025-11-14)

**Completed (94% of Phase 4.5.3):**
- ✅ Gradle Build System fully functional
- ✅ GitHub Actions workflow migrated to Gradle
- ✅ Wave 1: 3 files converted (DatabaseConstants, AppLogger, CompletionEntity) - 235 → 218 lines
- ✅ Wave 4: 2 files converted (UpdateChecker, UpdateInstaller) - 274 → 256 lines
- ✅ Wave 5: 3 files converted (TaskEntity, TaskDao, TaskDatabase) - 464 → 257 lines
- ✅ Wave 6: 3 files converted (TaskFilterManager, TaskListAdapter, TaskDialogHelper) - 745 → 768 lines
- ✅ Wave 7: 1 file converted (MainActivity) - 277 → 282 lines
- ✅ Wave 8: 1 file converted (TaskDialogHelper - again) - 368 → 404 lines
- ✅ Wave 9: 1 file converted (TaskActivity) - 393 → 385 lines
- ✅ Build time: ~3 minutes on GitHub Actions
- ✅ Version: v0.3.36 (Build 336)

**Status:**
- 17 of 18 files converted (94%)
- ~2,570 of ~3,500 target Kotlin lines (73%)
- Wave 10 pending (1 file remaining: TaskDatabaseHelper - 808 lines)

**Wave Results:**
- Wave 1: -7% lines (235 → 218)
- Wave 4: -7% lines (274 → 256)
- Wave 5: -45% lines (464 → 257)
- Wave 6: +3% lines (745 → 768)
- Wave 7: +2% lines (277 → 282)
- Wave 8: +10% lines (368 → 404)
- Wave 9: -2% lines (393 → 385)
- **Combined: -14% reduction** (2,756 Java → 2,370 Kotlin)

**Next Steps:**
- Wave 10: TaskDatabaseHelper (808 lines - largest file) - final conversion
- After Wave 10: Testing, documentation, and Phase 4.5.3 completion

**Actual Time So Far:** ~12 hours (Gradle setup + Waves 1,4-9)

---

## Phase 4.5.4: Package Renaming (1-2 days)

**Goal:** Simplify package structure by removing legacy "helloworld" suffix
**When:** After Kotlin migration completes (Phase 4.5.3)
**Why:** Reduces path depth by 2 levels, removes legacy naming confusion

### Problem

**Current Package:** `com.secretary.helloworld`
**Current Path:** `app/src/main/java/com/secretary/helloworld/shared/database/CLAUDE.md` (8 levels deep!)

**Target Package:** `com.secretary`
**Target Path:** `app/src/main/java/com/secretary/shared/database/CLAUDE.md` (6 levels deep)

**Savings:** 2 directory levels removed, cleaner imports, shorter paths

**Origin:** "helloworld" was a placeholder when project started, never changed

### Active TODOs

**CRITICAL:**
- [ ] Update package declarations in all Kotlin files
  - GOAL: Change `package com.secretary.helloworld.*` → `package com.secretary.*`
  - Location: All 18+ Kotlin files (after Phase 4.5.3 completion)
  - Action: Find/replace in all .kt files
  - Files affected: All files in core/, shared/, features/, app/

- [ ] Update namespace in build.gradle.kts
  - GOAL: Change `namespace = "com.secretary.helloworld"` → `namespace = "com.secretary"`
  - Location: `app/build.gradle.kts:8`
  - Action: Single line change

- [ ] Update applicationId in build.gradle.kts
  - GOAL: Change `applicationId = "com.secretary.helloworld"` → `applicationId = "com.secretary"`
  - Location: `app/build.gradle.kts:12`
  - Action: Single line change
  - **IMPORTANT:** This changes the package name users see in app store

- [ ] Update AndroidManifest.xml package reference
  - GOAL: Remove or verify package attribute (should use namespace from build.gradle.kts)
  - Location: `app/src/main/AndroidManifest.xml`
  - Action: Remove legacy `package="com.secretary.helloworld"` attribute

- [ ] Move directory structure
  - GOAL: Physically move files to new package path
  - Current: `app/src/main/java/com/secretary/helloworld/`
  - Target: `app/src/main/java/com/secretary/`
  - Action: `mv app/src/main/java/com/secretary/helloworld/* app/src/main/java/com/secretary/`
  - Then: `rmdir app/src/main/java/com/secretary/helloworld`

**HIGH:**
- [ ] Update all import statements
  - GOAL: Change all imports from `com.secretary.helloworld.*` → `com.secretary.*`
  - Location: All .kt files
  - Action: Find/replace across entire codebase
  - Estimate: 50-100 import statements

- [ ] Test build after renaming
  - GOAL: Verify Gradle build succeeds with new package
  - Action: `./gradlew assembleDebug`
  - Expected: Clean build, no import errors

- [ ] Update documentation
  - GOAL: Update all code references in CLAUDE.md files
  - Files: All CLAUDE.md files showing package examples
  - Action: Replace package examples throughout docs

**MEDIUM:**
- [ ] Update testing references (if tests exist)
  - GOAL: Update test package structure
  - Location: `app/src/test/` and `app/src/androidTest/`
  - Action: Mirror main source package changes

- [ ] Clean up legacy package references in comments
  - GOAL: Remove any stale "helloworld" mentions in comments
  - Action: Grep for "helloworld" and verify no code references remain

### Technical Details

**Before:**
```kotlin
// File: app/src/main/java/com/secretary/helloworld/core/logging/AppLogger.kt
package com.secretary.helloworld.core.logging

// File: app/src/main/java/com/secretary/helloworld/app/MainActivity.kt
import com.secretary.helloworld.core.logging.AppLogger
```

**After:**
```kotlin
// File: app/src/main/java/com/secretary/core/logging/AppLogger.kt
package com.secretary.core.logging

// File: app/src/main/java/com/secretary/app/MainActivity.kt
import com.secretary.core.logging.AppLogger
```

**build.gradle.kts changes:**
```kotlin
// BEFORE
android {
    namespace = "com.secretary.helloworld"

    defaultConfig {
        applicationId = "com.secretary.helloworld"
    }
}

// AFTER
android {
    namespace = "com.secretary"

    defaultConfig {
        applicationId = "com.secretary"
    }
}
```

### Migration Strategy

**Option 1: All-at-once (RECOMMENDED)**
1. Create git branch `refactoring/package-rename`
2. Use IDE refactoring: Right-click package → Refactor → Rename
3. Manually update build.gradle.kts namespace + applicationId
4. Move directory structure
5. Build and verify
6. Commit and push

**Option 2: Gradual (NOT recommended)**
- Complex due to package name changes affecting entire codebase
- High risk of missed imports

**Rollback Plan:**
- Git revert if build fails
- Package rename is atomic - either all works or none

### Deliverables

- [ ] Package renamed: `com.secretary.helloworld` → `com.secretary`
- [ ] Directory structure simplified (2 levels removed)
- [ ] All imports updated
- [ ] build.gradle.kts updated (namespace + applicationId)
- [ ] AndroidManifest.xml cleaned up
- [ ] Build verified: `./gradlew assembleRelease` succeeds
- [ ] Documentation updated (all CLAUDE.md files)
- [ ] APK tested: App installs and runs correctly

**Estimated time:** 1-2 days (mostly testing and verification)

**Dependencies:** Phase 4.5.3 (Kotlin Migration) must be 100% complete

**Risks:**
- Low: IDE refactoring handles most work
- Medium: Testing required to catch any missed references
- Low: Can revert via git if issues arise

---

## Phase 4.5.5: Domain Layer - Business Logic (4-5 days)

**Goal:** Extract business logic from database and UI into pure domain layer
**When:** After Room migration completes
**Why:** Enables testability, single responsibility, clear boundaries

### Active TODOs

**CRITICAL:**
- [ ] Refactor TaskDatabaseHelper God-Class (806 lines → ~200)
  - GOAL: Break down monolithic class into single-responsibility components
  - Current: TaskDatabaseHelper does CRUD + Recurrence + Streaks + Period calculations
  - Target: TaskDatabaseHelper → only data access (replaced by Room in 4.5.3)
  - Business logic → Use Cases and Services (this phase)

**HIGH:**
- [ ] Create pure domain models
  - GOAL: Data classes with NO Android dependencies
  - Location: `features/tasks/domain/model/`
  - Models:
    - `Task.java` - Pure task data (no getRecurrenceString(), no Android formatting)
    - `RecurrenceRule.java` - Encapsulate recurrence logic
    - `Completion.java` - Completion record
  - Principle: Can be tested without Android framework
- [ ] Define Repository interfaces
  - GOAL: Abstract data access, enable mocking
  - Location: `features/tasks/domain/repository/TaskRepository.java`
  - Methods: getAllTasks(), getTaskById(), insertTask(), updateTask(), deleteTask()
  - Note: Interface in domain/, implementation in data/ (dependency inversion)
- [ ] Extract Use Cases from TaskDatabaseHelper
  - GOAL: Single-purpose business logic units
  - Location: `features/tasks/domain/usecase/`
  - Use Cases to create:
    - `CompleteTaskUseCase.java` - Handle task completion + streak + recurrence
    - `CreateTaskUseCase.java` - Validate and create new task
    - `UpdateTaskUseCase.java` - Update existing task
    - `DeleteTaskUseCase.java` - Delete task and cleanup
    - `ResetDueRecurringTasksUseCase.java` - Handle overdue recurring tasks
  - Each: Single public method `execute()` or `invoke()`
- [ ] Extract Services for complex logic
  - GOAL: Reusable business logic components
  - Location: `features/tasks/domain/service/`
  - Services:
    - `RecurrenceService.java` - All recurrence calculations (from TaskDatabaseHelper lines 400-600)
      - Methods: calculateNextDueDate(), isInCurrentPeriod(), handleIntervalCompletion(), handleFrequencyCompletion()
    - `StreakService.java` - Streak calculations (from TaskDatabaseHelper + TaskStatistics)
      - Methods: calculateStreak(), updateStreak(), checkStreakBroken()
  - Principle: Stateless, testable, no Android dependencies
- [ ] Extract Statistics Use Cases
  - GOAL: Separate statistics calculation from UI
  - Location: `features/statistics/domain/usecase/`
  - Use Cases:
    - `CalculateStreakUseCase.java` - Calculate current and longest streaks
    - `GetTaskStatisticsUseCase.java` - Aggregate stats (completions, rates, averages)

**MEDIUM:**
- [ ] Write unit tests for domain layer
  - GOAL: 70%+ coverage for Use Cases and Services
  - Location: `devkit/testing/domain/`
  - Priority: RecurrenceService, StreakService, CompleteTaskUseCase
  - Framework: JUnit + Mockito
  - Example:
    ```java
    @Test
    public void testCompleteTask_UpdatesStreak() {
        // Given
        Task task = new Task(...);
        TaskRepository mockRepo = mock(TaskRepository.class);
        StreakService streakService = new StreakService();
        CompleteTaskUseCase useCase = new CompleteTaskUseCase(mockRepo, streakService, ...);

        // When
        useCase.execute(task.getId());

        // Then
        verify(mockRepo).updateTask(argThat(t -> t.getCurrentStreak() == task.getCurrentStreak() + 1));
    }
    ```

### Technical Details

**Use Case example:**
```java
// features/tasks/domain/usecase/CompleteTaskUseCase.java
public class CompleteTaskUseCase {
    private final TaskRepository taskRepository;
    private final StreakService streakService;
    private final RecurrenceService recurrenceService;

    public CompleteTaskUseCase(TaskRepository taskRepository,
                                StreakService streakService,
                                RecurrenceService recurrenceService) {
        this.taskRepository = taskRepository;
        this.streakService = streakService;
        this.recurrenceService = recurrenceService;
    }

    public void execute(long taskId) {
        Task task = taskRepository.getTaskById(taskId);
        if (task == null) return;

        // Update streak
        Task taskWithStreak = streakService.updateStreak(task);

        // Handle recurrence
        Task finalTask;
        if (taskWithStreak.getRecurrence() != null) {
            finalTask = recurrenceService.handleCompletion(taskWithStreak);
        } else {
            finalTask = taskWithStreak.markCompleted();
        }

        taskRepository.updateTask(finalTask);
    }
}
```

**Service example:**
```java
// features/tasks/domain/service/RecurrenceService.java
public class RecurrenceService {
    public Task handleCompletion(Task task) {
        RecurrenceRule rule = task.getRecurrence();
        if (rule == null) return task;

        switch (rule.getType()) {
            case INTERVAL:
                return handleIntervalCompletion(task, rule);
            case FREQUENCY:
                return handleFrequencyCompletion(task, rule);
            default:
                return task;
        }
    }

    private Task handleIntervalCompletion(Task task, RecurrenceRule rule) {
        // Logic extracted from TaskDatabaseHelper.resetIntervalTask()
        long nextDueDate = calculateNextDueDate(task.getDueDate(), rule);
        return task.copy()
            .withCompleted(false)
            .withDueDate(nextDueDate)
            .withLastCompletedDate(System.currentTimeMillis())
            .build();
    }

    private Task handleFrequencyCompletion(Task task, RecurrenceRule rule) {
        // Logic extracted from TaskDatabaseHelper.incrementFrequencyProgress()
        int newCompletions = task.getCompletionsThisPeriod() + 1;
        boolean needsReset = checkPeriodBoundary(task, rule);

        if (needsReset) {
            return task.copy()
                .withCompletionsThisPeriod(1)
                .withPeriodStart(System.currentTimeMillis())
                .build();
        } else {
            return task.copy()
                .withCompletionsThisPeriod(newCompletions)
                .build();
        }
    }

    private long calculateNextDueDate(long currentDueDate, RecurrenceRule rule) {
        // Complex date calculation logic
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentDueDate);

        switch (rule.getUnit()) {
            case DAY:
                cal.add(Calendar.DAY_OF_YEAR, rule.getAmount());
                break;
            case WEEK:
                cal.add(Calendar.WEEK_OF_YEAR, rule.getAmount());
                break;
            // ... etc
        }

        return cal.getTimeInMillis();
    }
}
```

**Before vs After:**
```
BEFORE (TaskDatabaseHelper - 806 lines):
- CRUD operations         (~200 lines)
- Recurrence logic        (~200 lines) ← Extract to RecurrenceService
- Streak tracking         (~80 lines)  ← Extract to StreakService
- Period calculations     (~120 lines) ← Extract to RecurrenceService
- Statistics delegation   (~50 lines)  ← Extract to Use Cases
- Database migrations     (~100 lines) ← Keep in data layer
- Query building          (~50 lines)  ← Replaced by Room

AFTER:
- TaskDatabaseHelper: DELETED (replaced by Room)
- RecurrenceService: ~250 lines (pure business logic)
- StreakService: ~100 lines (pure business logic)
- Use Cases: ~30-50 lines each (orchestration)
- Repository: ~150 lines (data access abstraction)
```

**Deliverables:**
- ✅ TaskDatabaseHelper refactored to ~200 lines or deleted
- ✅ 5+ Use Cases created and tested
- ✅ RecurrenceService and StreakService extracted
- ✅ Pure domain models (no Android dependencies)
- ✅ 70%+ test coverage for domain layer
- ✅ All business logic testable without Android framework

**Estimated time:** 4-5 days

---

## Phase 4.5.6: Presentation Layer - MVVM (3-4 days)

**Goal:** Separate UI from business logic using MVVM pattern
**When:** After domain layer is complete
**Why:** Reactive UI, testable presentation logic, modern Android

### Active TODOs

**HIGH:**
- [ ] Create ViewModels for activities
  - GOAL: Move UI state and logic out of Activities
  - Location: `features/tasks/presentation/TaskViewModel.java`, `features/statistics/presentation/StatisticsViewModel.java`
  - Responsibilities:
    - Hold UI state (task list, statistics, filters)
    - Orchestrate Use Cases
    - Expose data via LiveData or StateFlow
  - Dependencies: Use Cases injected via constructor
- [ ] Refactor TaskActivity (392 lines → ~150 lines)
  - GOAL: Activity only handles UI lifecycle and user interactions
  - Current: TaskActivity does UI + filtering + statistics + DB access
  - Target: TaskActivity → observe ViewModel, render UI, dispatch events
  - Remove: Direct DB access, business logic, calculations
  - Keep: onCreate(), UI setup, click listeners
- [ ] Extract dialogs from TaskDialogHelper (367 lines)
  - GOAL: Modular dialog components
  - Location: `features/tasks/presentation/dialog/`
  - Dialogs: AddTaskDialog, EditTaskDialog, CompletionDialog
  - Pattern: DialogFragment with ViewModel communication
- [ ] Implement basic Dependency Injection
  - GOAL: Manual DI for ViewModels (Hilt not viable in Termux)
  - Location: `core/di/AppModule.java`
  - Pattern: Factory classes for ViewModel creation
  - Example:
    ```java
    public class TaskViewModelFactory {
        public static TaskViewModel create(Context context) {
            TaskDatabase db = TaskDatabase.getDatabase(context);
            TaskRepository repo = new TaskRepositoryImpl(db.taskDao());
            CompleteTaskUseCase completeUseCase = new CompleteTaskUseCase(repo, ...);
            return new TaskViewModel(completeUseCase, ...);
        }
    }
    ```

**MEDIUM:**
- [ ] Implement reactive UI updates
  - GOAL: UI automatically reflects data changes
  - Pattern: ViewModel exposes LiveData, Activity observes
  - Example:
    ```java
    // TaskViewModel
    private MutableLiveData<List<Task>> tasksLiveData = new MutableLiveData<>();
    public LiveData<List<Task>> getTasks() { return tasksLiveData; }

    // TaskActivity
    viewModel.getTasks().observe(this, tasks -> {
        adapter.submitList(tasks);
    });
    ```
- [ ] Refactor TaskFilterManager
  - GOAL: Move filter logic to ViewModel
  - Current: TaskFilterManager in separate class
  - Target: Filtering logic in TaskViewModel
  - Benefits: Reactive filtering, testable
- [ ] Move statistics to StatisticsViewModel
  - GOAL: Centralize statistics logic
  - Location: `features/statistics/presentation/StatisticsViewModel.java`
  - Exposes: todayCompletions, weekCompletions, currentStreak, longestStreak
  - Updates: Automatically when tasks change

**LOW:**
- [ ] Add loading and error states
  - GOAL: Better UX during async operations
  - Pattern: Sealed class or enum for UI state
  - States: Loading, Success, Error
  - Displayed: Progress bars, error messages

### Technical Details

**ViewModel example:**
```java
// features/tasks/presentation/TaskViewModel.java
public class TaskViewModel extends ViewModel {
    private final GetAllTasksUseCase getAllTasksUseCase;
    private final CompleteTaskUseCase completeTaskUseCase;
    private final CreateTaskUseCase createTaskUseCase;

    private final MutableLiveData<List<Task>> tasksLiveData = new MutableLiveData<>();
    private final MutableLiveData<TaskStatistics> statisticsLiveData = new MutableLiveData<>();

    public TaskViewModel(GetAllTasksUseCase getAllTasksUseCase,
                          CompleteTaskUseCase completeTaskUseCase,
                          CreateTaskUseCase createTaskUseCase) {
        this.getAllTasksUseCase = getAllTasksUseCase;
        this.completeTaskUseCase = completeTaskUseCase;
        this.createTaskUseCase = createTaskUseCase;
    }

    public LiveData<List<Task>> getTasks() {
        return tasksLiveData;
    }

    public void loadTasks() {
        // In real impl: use Executors or RxJava for async
        new Thread(() -> {
            List<Task> tasks = getAllTasksUseCase.execute();
            tasksLiveData.postValue(tasks);
        }).start();
    }

    public void completeTask(long taskId) {
        new Thread(() -> {
            completeTaskUseCase.execute(taskId);
            loadTasks(); // Refresh
        }).start();
    }

    public void createTask(Task task) {
        new Thread(() -> {
            createTaskUseCase.execute(task);
            loadTasks(); // Refresh
        }).start();
    }
}
```

**Activity refactoring:**
```java
// features/tasks/presentation/TaskActivity.java
// BEFORE (392 lines)
public class TaskActivity extends AppCompatActivity {
    private TaskDatabaseHelper dbHelper; // Direct DB access!
    private ListView taskListView;
    private TaskListAdapter adapter;

    private void loadTasks() {
        taskList.clear();
        taskList.addAll(dbHelper.getAllTasks()); // Direct DB!
        adapter.notifyDataSetChanged();
    }

    private void applyFilters() {
        // Filtering logic in UI!
    }

    private void updateStatistics() {
        int todayCount = dbHelper.getTasksCompletedToday(); // Direct DB!
        // ... display logic
    }
}

// AFTER (~150 lines)
public class TaskActivity extends AppCompatActivity {
    private TaskViewModel viewModel; // ViewModel!
    private ListView taskListView;
    private TaskListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasks);

        // Initialize ViewModel
        viewModel = TaskViewModelFactory.create(this);

        // Setup UI
        setupViews();
        observeViewModel();

        // Load data
        viewModel.loadTasks();
    }

    private void observeViewModel() {
        viewModel.getTasks().observe(this, tasks -> {
            adapter.submitList(tasks); // Automatic UI update!
        });

        viewModel.getStatistics().observe(this, stats -> {
            updateStatisticsUI(stats);
        });
    }

    private void setupViews() {
        taskListView = findViewById(R.id.taskListView);
        adapter = new TaskListAdapter(this);
        taskListView.setAdapter(adapter);

        findViewById(R.id.addTaskButton).setOnClickListener(v -> {
            showAddTaskDialog();
        });
    }
}
```

**Dependency Injection (manual):**
```java
// core/di/AppModule.java
public class AppModule {
    private static TaskDatabase database;
    private static TaskRepository taskRepository;

    public static TaskRepository provideTaskRepository(Context context) {
        if (taskRepository == null) {
            database = TaskDatabase.getDatabase(context);
            taskRepository = new TaskRepositoryImpl(database.taskDao());
        }
        return taskRepository;
    }

    public static RecurrenceService provideRecurrenceService() {
        return new RecurrenceService();
    }

    public static StreakService provideStreakService() {
        return new StreakService();
    }
}

// core/di/TaskViewModelFactory.java
public class TaskViewModelFactory {
    public static TaskViewModel create(Context context) {
        TaskRepository repo = AppModule.provideTaskRepository(context);
        RecurrenceService recurrenceService = AppModule.provideRecurrenceService();
        StreakService streakService = AppModule.provideStreakService();

        CompleteTaskUseCase completeUseCase = new CompleteTaskUseCase(repo, streakService, recurrenceService);
        GetAllTasksUseCase getAllUseCase = new GetAllTasksUseCase(repo);
        CreateTaskUseCase createUseCase = new CreateTaskUseCase(repo);

        return new TaskViewModel(getAllUseCase, completeUseCase, createUseCase);
    }
}
```

**Before vs After:**
```
BEFORE:
- TaskActivity: 392 lines (UI + Logic + DB)
- TaskDialogHelper: 367 lines (All dialogs)
- No ViewModels
- Direct DB access from UI
- Not testable

AFTER:
- TaskActivity: ~150 lines (Only UI)
- TaskViewModel: ~200 lines (Testable logic)
- AddTaskDialog: ~80 lines
- EditTaskDialog: ~80 lines
- CompletionDialog: ~80 lines
- Fully testable without Android framework (ViewModel)
```

**Deliverables:**
- ✅ ViewModels for tasks and statistics
- ✅ TaskActivity refactored to ~150 lines
- ✅ Dialogs extracted to separate classes
- ✅ Manual DI factory classes
- ✅ Reactive UI with LiveData
- ✅ No direct DB access from UI
- ✅ Testable presentation logic

**Estimated time:** 3-4 days

---

## Phase 4.5.7: Testing & Documentation (2-3 days)

**Goal:** Comprehensive tests, updated documentation, final cleanup
**When:** Throughout phases 4.5.1-4.5.5 + final pass
**Why:** Ensure refactoring didn't break anything, enable future development

### Active TODOs

**CRITICAL:**
- [ ] Achieve 70%+ test coverage for domain layer
  - GOAL: Confidence in business logic correctness
  - Location: `devkit/testing/domain/`
  - Priority tests:
    - RecurrenceService (all methods)
    - StreakService (all methods)
    - CompleteTaskUseCase
    - ResetDueRecurringTasksUseCase
  - Tools: JUnit, Mockito
- [ ] Integration tests for data layer
  - GOAL: Verify Room database works correctly
  - Location: `devkit/testing/data/`
  - Tests:
    - Repository saves and retrieves tasks
    - DAO queries return correct results
    - Migrations preserve data
  - Challenge: Requires Android instrumentation (run on GitHub Actions)

**HIGH:**
- [ ] Create ARCHITECTURE.md
  - GOAL: Document new architecture decisions
  - Location: `docs/ARCHITECTURE.md`
  - Content:
    - Architecture diagram (Hybrid Feature + Clean Layers)
    - Dependency rules (Presentation → Domain → Data)
    - Feature module structure
    - Adding new features guide
- [ ] Create DEBUGGING.md
  - GOAL: Consolidate debugging workflows
  - Location: `docs/DEBUGGING.md`
  - Content:
    - HTTP log access (curl localhost:8080)
    - Logcat filtering by class
    - Common issues and solutions
    - Testing workflows
- [ ] Update CLAUDE.md
  - GOAL: Reflect new architecture in developer guide
  - Sections to update:
    - Project structure (new directories)
    - Architecture overview (Clean Architecture)
    - Common workflows (using Use Cases, ViewModels)
    - File paths (all references)
- [ ] Update README.md
  - GOAL: User-facing documentation reflects new structure
  - Updates: Architecture section, development guide
- [ ] Delete obsolete files
  - GOAL: Clean codebase, no legacy code
  - Delete:
    - `TaskDatabaseHelper.java` (replaced by Room + Repository)
    - `DatabaseConstants.java` (replaced by Room entities)
    - Already deleted in 4.5.1: LogServer, LogProvider, NanoHTTPD
  - Verify: No references remain

**MEDIUM:**
- [ ] UI tests for critical flows
  - GOAL: Catch regressions in user workflows
  - Location: `devkit/testing/ui/`
  - Tests: Task creation, task completion, recurrence handling
  - Tools: Espresso (run on GitHub Actions)
- [ ] Performance testing
  - GOAL: Ensure refactoring didn't degrade performance
  - Tests:
    - Database query performance (getAllTasks with 1000+ tasks)
    - UI responsiveness (list scrolling)
    - Memory usage (no leaks)
- [ ] Code review and cleanup
  - GOAL: Consistent code style, remove TODOs
  - Actions:
    - Format all files consistently
    - Remove debug comments
    - Standardize naming conventions
    - Fix compiler warnings

**LOW:**
- [ ] Generate code coverage reports
  - GOAL: Visibility into test coverage
  - Tool: JaCoCo (integrate with GitHub Actions)
  - Display: Coverage badge in README.md
- [ ] Create utility scripts
  - GOAL: Common development tasks
  - Scripts:
    - `devkit/utilities/run_tests.sh` - Run all tests
    - `devkit/utilities/coverage_report.sh` - Generate coverage
    - `devkit/utilities/clean_build.sh` - Clean build artifacts

### Technical Details

**Test coverage targets:**
```
Domain Layer (Use Cases, Services):  70%+  ← CRITICAL
Data Layer (Repository, DAOs):       50%+  ← HIGH
Presentation Layer (ViewModels):     50%+  ← MEDIUM
UI Layer (Activities):                30%+  ← LOW (manual testing)
```

**Test examples:**

**Unit Test (RecurrenceService):**
```java
// devkit/testing/domain/RecurrenceServiceTest.java
@Test
public void testCalculateNextDueDate_IntervalDaily() {
    RecurrenceService service = new RecurrenceService();
    RecurrenceRule rule = new RecurrenceRule(RecurrenceType.INTERVAL, 3, TimeUnit.DAY);

    long currentDue = System.currentTimeMillis();
    long nextDue = service.calculateNextDueDate(currentDue, rule);

    long expectedDue = currentDue + (3 * 24 * 60 * 60 * 1000L);
    assertEquals(expectedDue, nextDue, 1000); // 1 second tolerance
}

@Test
public void testHandleFrequencyCompletion_ResetsAtPeriodBoundary() {
    // Given
    RecurrenceService service = new RecurrenceService();
    RecurrenceRule rule = new RecurrenceRule(RecurrenceType.FREQUENCY, 3, TimeUnit.WEEK);
    Task task = new Task.Builder()
        .withCompletionsThisPeriod(2)
        .withPeriodStart(System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)) // 8 days ago
        .withRecurrence(rule)
        .build();

    // When
    Task result = service.handleFrequencyCompletion(task, rule);

    // Then
    assertEquals(1, result.getCompletionsThisPeriod()); // Reset to 1
    assertTrue(result.getPeriodStart() > task.getPeriodStart()); // New period
}
```

**Integration Test (Repository):**
```java
// devkit/testing/data/TaskRepositoryTest.java
@Test
public void testRepository_SaveAndRetrieveTask() {
    // Given
    Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    TaskDatabase db = TaskDatabase.getDatabase(context);
    TaskRepository repository = new TaskRepositoryImpl(db.taskDao());

    Task task = new Task.Builder()
        .withTitle("Test Task")
        .withDescription("Test Description")
        .withPriority(Priority.HIGH)
        .build();

    // When
    long id = repository.insertTask(task);
    Task retrieved = repository.getTaskById(id);

    // Then
    assertNotNull(retrieved);
    assertEquals("Test Task", retrieved.getTitle());
    assertEquals("Test Description", retrieved.getDescription());
    assertEquals(Priority.HIGH, retrieved.getPriority());
}
```

**Documentation structure:**

**ARCHITECTURE.md:**
```markdown
# AI Secretary - Architecture

## Overview
Clean Architecture with feature-based modules

## Layers
- **Presentation:** Activities, ViewModels, Adapters
- **Domain:** Use Cases, Services, Models (no Android deps)
- **Data:** Repository, Room DAOs, Entities

## Dependency Rules
Presentation → Domain → Data (never reverse!)

## Feature Modules
- tasks/ - Task management
- statistics/ - Statistics and motivation

[Detailed diagrams and examples...]
```

**DEBUGGING.md:**
```markdown
# Debugging Guide

## HTTP Logs
```bash
curl http://localhost:8080/logs
```

## Logcat Filtering
```bash
logcat | grep -E "(TaskViewModel|RecurrenceService|StreakService)"
```

## Common Issues
1. Task not appearing...
2. Recurrence not resetting...
[Solutions...]
```

**Deliverables:**
- ✅ 70%+ domain layer test coverage
- ✅ Integration tests for data layer
- ✅ UI tests for critical flows
- ✅ ARCHITECTURE.md created
- ✅ DEBUGGING.md created
- ✅ CLAUDE.md, README.md updated
- ✅ All obsolete files deleted
- ✅ Code review completed
- ✅ Performance validated
- ✅ Coverage reports generated

**Estimated time:** 2-3 days (ongoing throughout refactoring)

---

## Summary: Complete Timeline

| Phase | Focus | Duration | Cumulative | Status |
|-------|-------|----------|------------|--------|
| 4.5.1: Cleanup | Delete redundant code, setup tests | ~2 hours | 0.25 days | ✅ COMPLETE (2025-11-13) |
| 4.5.2: Structure | Create directories, move files | 2-3 days | 3 days | ⏳ Next Up |
| 4.5.3: Data Layer | Room migration | 3-4 days | 7 days | ⏳ Planned |
| 4.5.4: Domain Layer | Extract Use Cases & Services | 4-5 days | 12 days | ⏳ Planned |
| 4.5.5: Presentation | MVVM with ViewModels | 3-4 days | 16 days | ⏳ Planned |
| 4.5.6: Testing | Tests + Documentation | 2-3 days | 19 days | ⏳ Planned |

**Total: 15-21 days (3-4 weeks full-time)**
**Progress:** 1/6 phases complete (16.7%)

**Key Milestones:**
- ✅ 2025-11-13: Codebase reduced by 13.4% (496 lines deleted)
  - Phase 4.5.1 completed in ~2 hours (much faster than estimated!)
- Day 5: New structure in place, builds successfully
- Day 9: Room ORM functional, Repository pattern working
- Day 14: Business logic extracted, 70% test coverage
- Day 18: MVVM implemented, UI reactive
- Day 21: Fully documented, production-ready

**Post-Refactor State:**
```
BEFORE:
❌ 16 files, 3,712 lines
❌ ~40% redundancy (1,500 lines waste)
❌ 0% test coverage
❌ No architecture
❌ God-Classes (806 lines)
❌ Not testable, not maintainable

AFTER:
✅ ~35 files, ~3,200 lines (clean)
✅ 0% redundancy
✅ 70%+ test coverage (domain)
✅ Clean Architecture (3 layers)
✅ Single Responsibility (~150 lines/class avg)
✅ Fully testable, maintainable, scalable
✅ 2-3x faster feature development
✅ 70% fewer bugs
```

**ROI:** 3-4 weeks investment → 2-3x productivity boost for all future development

---

## 🔮 Future Phases

### Phase 5: Intelligent Planning (4-5 weeks)
**Goal:** AI-powered task scheduling and prioritization

**Key Features:**
- Multi-factor scoring algorithm (priority, due date, duration, difficulty, time of day)
- Smart daily task selection
- Optimal task ordering suggestions
- "Next Task" recommendation

**Prerequisites:** Phase 4 statistics data available for algorithm input

---

### Phase 6: Widget & Polish (3-4 weeks)
**Goal:** Home screen integration and UI refinement

**Key Features:**
- Home screen widget showing next task and today's list
- Quick-complete from widget
- Dark mode support
- Animations and transitions
- Accessibility improvements
- Custom app icon

**Prerequisites:** Phases 1-5 stable and tested

---

## 🛠️ Technical Debt

### Active Issues

**CRITICAL:**
- None currently - Phase 0 security issues resolved (v0.1.2)

**HIGH:**
- Package name inconsistency: `com.secretary.helloworld` should be `com.secretary`
  - Impact: Awkward imports, unprofessional naming
  - Effort: 2-3 hours (refactor all imports, update manifest)
  - When: Before public release or major refactoring

**MEDIUM:**
- No external libraries: Limited by Termux/aapt2 build process
  - Cannot use Room, Material Components, etc.
  - Workaround: Manual implementations
  - Long-term: Consider Kotlin migration with full Gradle on GitHub Actions
- No unit tests: Testing framework not set up
  - Risk: Regressions when refactoring
  - Mitigation: Manual testing via HTTP logs
  - When: After Phase 6 or during major refactoring

**LOW:**
- Mixed language comments (English code, some German comments)
  - Cleanup: Standardize to English
  - When: During code cleanup passes
- Manual thread management in UpdateChecker
  - Better: ExecutorService or Kotlin Coroutines
  - When: If threading issues arise

### Architecture Considerations

**Current:** Simple single-activity with dialogs, flat package structure
- ✅ **Pros:** Lightweight, easy to understand, fast development
- ⚠️ **Cons:** Will need refactoring for larger feature sets

**Future (Post-MVP):** Consider layer-based architecture
- domain/ - Business logic and models
- data/ - Database and repositories
- presentation/ - UI and ViewModels

**Decision Point:** After Phase 6 MVP, evaluate if refactoring needed before Phase 7+

---

## 📈 Development Timeline

| Phase | Duration | Status | Version Range |
|-------|----------|--------|---------------|
| Phase 0: Foundation | 2 weeks | ✅ Complete | v0.0.x - v0.1.x |
| Phase 1: Foundation | 3-4 weeks | ✅ Complete | v0.2.x |
| Phase 2: Core Tasks | 4-5 weeks | ✅ Complete | v0.3.0 - v0.3.7 |
| Phase 3: Tracking | 3-4 weeks | ✅ Complete | v0.3.8 - v0.3.10 |
| Phase 4: Motivation | 2-3 weeks | 🚧 In Progress | v0.3.11+ |
| Phase 4.5: Refactor | 3-4 weeks | ⏳ Planned | v0.3.30+ |
| Phase 5: Planning | 4-5 weeks | ⏳ Planned | v0.4.x |
| Phase 6: Widget | 3-4 weeks | ⏳ Planned | v0.5.x |
| **MVP Release** | **~20-25 weeks** | **🎯 Target** | **v1.0.0** |

**Progress:** ~14 weeks completed, ~9-13 weeks remaining for MVP

**Note:** Phase 4.5 extended from 1-2 to 3-4 weeks to address critical architecture issues identified in ARCHITECTURE_AUDIT.md

---

## 🔗 Related Documentation

- **[CLAUDE.md](./CLAUDE.md)** - Complete developer guide (architecture, workflows, debugging)
- **[README.md](./README.md)** - Project overview and quick start
- **[docs/LOGGING_SYSTEM.md](./docs/LOGGING_SYSTEM.md)** - HTTP logging documentation
- **[docs/UPDATE_SYSTEM.md](./docs/UPDATE_SYSTEM.md)** - Auto-update mechanism
- **[~/CLAUDE.md](../CLAUDE.md)** - Termux environment guide

---

## 📝 Recent Changes

### 2025-11-13 - Phase 4.5 Architecture Refactor Expanded
- Added Phase 4.5: Architecture Refactor (3-4 weeks) with 6 sub-phases
- Based on ARCHITECTURE_AUDIT.md findings - addresses all critical issues
- Hybrid architecture: Feature modules + Clean Architecture layers
- Phase 4.5.1: Critical Cleanup (delete 469 lines redundant code)
- Phase 4.5.2: Package Structure (create directories, move files)
- Phase 4.5.3: Data Layer (Room ORM migration)
- Phase 4.5.4: Domain Layer (Use Cases, Services, extract from God-Classes)
- Phase 4.5.5: Presentation Layer (MVVM, ViewModels, LiveData)
- Phase 4.5.6: Testing & Documentation (70% coverage, ARCHITECTURE.md, DEBUGGING.md)
- Updated timeline: MVP now ~20-25 weeks (realistic estimate for complete refactor)

### 2025-11-13 - Documentation Refactor
- ROADMAP.md restructured to focus on current phase
- Reduced from 63KB to ~12KB by summarizing completed phases
- Added Priority Definitions matching project standards
- Moved detailed tech debt to separate archive section

### 2025-11-12 - Phase 3 Complete
- v0.3.11: Streak tracking implemented
- v0.3.10: Completion dialog with time/difficulty tracking
- v0.3.9: Task sorting and basic statistics
- Phase 4 (Motivation) now active

### 2025-11-12 - Phase 2 Complete
- v0.3.7: Categories, due dates, extended notes
- v0.3.6: Search and filter functionality
- v0.3.5: Task editing capability
- Recurrence system fully functional (INTERVAL + FREQUENCY)

### 2025-11-12 - Phase 0 Complete
- v0.1.2: GitHub token removed, repository made public
- v0.1.1: HTTP server for logging (localhost:8080)
- Auto-update and logging systems fully functional

---

**For detailed phase specifications and technical debt archive:** See previous versions or create separate ARCHITECTURE_DECISIONS.md if needed.

**Next Review:** After Phase 4 completion or major architectural decisions
