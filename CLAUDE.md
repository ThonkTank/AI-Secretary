# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**AI Secretary** is a native Android task management app with recurring task support, developed entirely in Termux on Android. This is a personal productivity app focused on the "Taskmaster" feature suite - a comprehensive system for managing daily, weekly, and long-term recurring tasks with intelligent planning and streak tracking.

**Current Status:** Phase 4.5.3 - COMPLETE ✅ (v0.3.40 - Build 340 - Kotlin + Gradle + Domain Infrastructure)
- ✅ Phase 0: Foundation Systems (100% complete)
- ✅ Phase 1: Taskmaster Foundation (100% complete)
- ✅ Phase 2: Core Task Management (100% complete)
- ✅ Phase 3: Tracking & Analytics (100% complete)
- ⏸️ Phase 4: Motivation & Statistics (30% complete - PAUSED for refactoring)
- 🚧 **Phase 4.5: Architecture Refactor (30% complete - IN PROGRESS)**
  - ✅ Phase 4.5.1: Critical Cleanup (COMPLETE)
  - ✅ Phase 4.5.2: Package Structure (COMPLETE)
  - ✅ Phase 4.5.3: Kotlin Migration + Gradle + Domain Infrastructure (Waves 1-10 COMPLETE - 100%)

**Repository:** https://github.com/ThonkTank/AI-Secretary
**Branch:** `refactoring/phase-4.5-architecture` (active development)

---

## Development Environment

### Hardware & System
- **Device:** Google Pixel 8 (aarch64)
- **Android Version:** Android 16 (API Level 36)
- **Development:** Termux (googleplay.2025.10.05)
- **Java:** OpenJDK 21.0.9
- **Build System:** Gradle 8.2 + Android Gradle Plugin 8.2.2
- **Language:** Kotlin 1.9.22 (migrating from Java)
- **Android SDK:** API 35 (compile), API 28-35 (runtime)

### Build System: Gradle on GitHub Actions
**Phase 4.5.3 Update:** Project now uses Gradle build system! All builds run on GitHub Actions with full Gradle support. Local Gradle DOES NOT work in Termux due to JVM libiconv errors.

---

## Build & Deployment

### Production Build (REQUIRED for all real work)

```bash
# 1. Update version in app/build.gradle.kts
#    - Increment versionCode (e.g., 340 → 341)
#    - Update versionName following phase-based scheme (e.g., 0.3.40 → 0.3.41)

# 2. Commit and push to trigger GitHub Actions
git add .
git commit -m "feat: your feature description"
git push origin refactoring/phase-4.5-architecture  # Use current branch

# 3. Monitor build status (takes ~4 minutes with Gradle)
export GH_TOKEN=$(cat ~/.github_token)
gh run watch  # Watch live
gh run view --log  # If build fails

# 4. Download APK when build completes
VERSION="0.3.40"  # Use your new version
gh release download "v$VERSION" -p "AISecretary-signed.apk" -D ~/storage/downloads/

# 5. Install APK
cd ~/storage/downloads
termux-media-scan AISecretary-signed.apk
termux-open AISecretary-signed.apk
```

**GitHub Workflow:** `.github/workflows/build-and-release.yml`
- Triggers: push to any branch or manual dispatch
- Build System: Gradle 8.2 + Android Gradle Plugin 8.2.2
- JDK: 21 (required for AGP 8.2+)
- Kotlin: 1.9.22
- Build command: `./gradlew assembleRelease --stacktrace`
- Build time: ~4 minutes
- Auto-creates releases with version tag from app/build.gradle.kts

### Local Development Build (Testing Only)

```bash
# For quick local testing - very limited capabilities
./build.sh

# Install
cp app_signed.apk ~/storage/downloads/
termux-open ~/storage/downloads/app_signed.apk
```

**Limitations:** No support for external libraries, manual resource compilation, no dependency management. Use only for quick tests.

---

## Architecture

**⚠️ ARCHITECTURE REFACTORING IN PROGRESS (Phase 4.5)**

**Current State:** Flat structure (all files in one package)
**Target State:** Clean Architecture with Presentation → Domain → Data layers

See [ROADMAP.md](ROADMAP.md) Phase 4.5 for detailed refactoring plan.

### Project Structure (BEFORE Phase 4.5.1 - Baseline)

```
AI-Secretary-latest/
├── src/com/secretary/          # 16 Java files (BEFORE cleanup)
│   ├── MainActivity.java              # Landing page with Settings menu
│   ├── TaskActivity.java              # Main task management UI
│   ├── Task.java                      # Task entity with recurrence logic
│   ├── TaskDatabaseHelper.java        # SQLite database (v4, two tables)
│   ├── TaskListAdapter.java           # ListView adapter for tasks
│   ├── TaskDialogHelper.java          # Task creation/edit dialogs
│   ├── TaskFilterManager.java         # Search/filter/sort logic
│   ├── TaskStatistics.java            # Analytics and streak calculations
│   ├── DatabaseConstants.java         # DB schema constants
│   ├── AppLogger.java                 # In-memory logging (500 lines)
│   ├── SimpleHttpServer.java          # HTTP server for log access
│   ├── ❌ LogProvider.java            # DELETED in Phase 4.5.1
│   ├── ❌ LogServer.java              # DELETED in Phase 4.5.1
│   ├── ❌ NanoHTTPD.java              # DELETED in Phase 4.5.1
│   ├── UpdateChecker.java             # GitHub Releases API client
│   └── UpdateInstaller.java           # APK download via DownloadManager
├── res/
│   ├── layout/
│   │   ├── activity_main.xml          # Landing page
│   │   ├── activity_tasks.xml         # Task list with filters
│   │   ├── task_list_item.xml         # Individual task item
│   │   ├── dialog_add_task.xml        # Task creation dialog
│   │   ├── dialog_completion.xml      # Task completion dialog
│   │   ├── dialog_settings.xml        # Settings with update check
│   │   └── dialog_logs.xml            # Log viewer dialog
│   ├── menu/main_menu.xml             # Action bar menu
│   └── values/strings.xml             # String resources
├── devkit/                        # Development tools (NEW in Phase 4.5.1)
│   ├── testing/                      # Test infrastructure
│   │   ├── domain/                   # Unit tests (future)
│   │   ├── data/                     # Integration tests (future)
│   │   ├── integration/              # E2E tests (future)
│   │   ├── fixtures/                 # Test data (future)
│   │   └── README.md                 # Testing documentation
│   ├── build/                        # Build scripts (future)
│   └── utilities/                    # Dev utilities (future)
├── docs/                          # Technical documentation
│   ├── REFACTORING_BASELINE.md       # System behavior baseline (NEW in 4.5.1)
│   ├── LOGGING_SYSTEM.md             # HTTP logging details
│   └── UPDATE_SYSTEM.md              # Auto-update mechanism
├── AndroidManifest.xml            # App manifest (ContentProvider removed in 4.5.1)
├── build.sh                       # Local build script (limited)
└── .github/workflows/
    └── build-and-release.yml      # Production build workflow
```

### Phase 4.5.1 Changes (Completed 2025-11-13)

**Files Deleted (496 lines total):**
- ❌ `LogServer.java` (148 lines) - Duplicate HTTP server using NanoHTTPD
- ❌ `LogProvider.java` (110 lines) - Unused ContentProvider
- ❌ `NanoHTTPD.java` (211 lines) - Overkill library for simple logging
- ❌ ContentProvider entry removed from AndroidManifest.xml

**Files Modified:**
- ✅ `AppLogger.java` - Now truly IN-MEMORY (87 lines, from 114 lines)
  - Removed `logFile` variable and `writeToFile()` method
  - Removed file-I/O imports (BufferedWriter, File, FileWriter, Environment)
  - Updated comments to reflect HTTP server access

**New Documentation:**
- ✅ `docs/REFACTORING_BASELINE.md` - System behavior baseline for regression testing
- ✅ `devkit/testing/README.md` - Testing infrastructure documentation

**Result:** 13.4% codebase reduction (496/3,712 lines), logging system simplified (5 files → 2 files)

### Key Components

#### Task Management Core

**Task.java** - Task entity with comprehensive fields
- Basic: id, title, description, category, createdAt, dueDate, isCompleted, priority
- Recurrence: Two types (INTERVAL and FREQUENCY), smart completion logic
- Streak Tracking: currentStreak, longestStreak, lastStreakDate
- ~320 lines with getters/setters and recurrence logic methods

**TaskDatabaseHelper.java** - SQLite database management (v4)
- Two tables: `tasks` (17 columns) and `completions` (6 columns)
- CRUD operations for tasks
- Recurrence handling: resetIntervalTask(), incrementFrequencyProgress()
- Database migrations from v1→v2→v3→v4
- Integrated with TaskStatistics for analytics

**TaskActivity.java** - Main task management UI
- Task list with priority-based display
- Filter controls: search, status, priority, category, sort
- Add/Edit/Delete/Complete task operations
- Statistics display (total, completed, active tasks)
- ~500+ lines including lifecycle management

**TaskDialogHelper.java** - Manages task dialogs
- Task creation dialog with all fields
- Task edit dialog (pre-filled)
- Task completion dialog with metadata
- Recurrence configuration UI
- Category selection spinner

**TaskFilterManager.java** - Search and filter logic
- Search by title/description
- Filter by status (all/active/completed)
- Filter by priority (Low/Medium/High/Urgent)
- Filter by category (10 categories)
- Sort by: priority, due date, category, created date, title

**TaskStatistics.java** - Analytics and calculations
- Completion tracking via `completions` table
- Streak calculation logic (currentStreak, longestStreak)
- Task completion statistics
- Time-based analytics

#### Foundation Systems (Phase 0)

**AppLogger.java** - Centralized logging system
- Singleton pattern with in-memory buffer (500 lines max)
- Three levels: INFO, DEBUG, ERROR
- Auto-trimming when buffer exceeds 500 entries
- Triple redundancy: memory + logcat + HTTP server
- Purpose: Enable Claude Code to monitor app behavior

**SimpleHttpServer.java** - HTTP server for log access
- Runs on localhost:8080
- Endpoints: `/logs` (all logs), `/` (help)
- Enables Claude Code to read logs via curl
- Single-threaded, synchronous request handling

**UpdateChecker.java** - GitHub Releases API integration
- Checks for new versions on GitHub
- Parses release JSON and extracts latest version
- Compares with current app version
- Callback interface for update availability

**UpdateInstaller.java** - APK download and installation
- Uses Android DownloadManager for APK download
- BroadcastReceiver for download completion
- Launches installation prompt when download completes

### Database Schema

**tasks table** (17 columns):
- id, title, description, category, created_at, due_date, is_completed, priority
- recurrence_type, recurrence_amount, recurrence_unit, last_completed_date
- completions_this_period, current_period_start
- current_streak, longest_streak, last_streak_date

**completions table** (6 columns):
- completion_id, task_id (FK), completed_at, time_spent, difficulty, notes

### Recurrence System

**Two Recurrence Types:**

1. **INTERVAL** ("Every X Y")
   - Examples: "Every 3 days", "Every 1 week"
   - Logic: Task resets when X time units pass since last completion
   - Implementation: `resetIntervalTask()` - uncompletes task and sets new due date

2. **FREQUENCY** ("X times per Y")
   - Examples: "3 times per week", "5 times per month"
   - Logic: Track completions within period; reset at period end
   - Implementation: `incrementFrequencyProgress()` - increments counter, resets at period boundary

**Time Units:** DAY, WEEK, MONTH, YEAR

### Categories System

10 predefined categories:
- General, Work, Personal, Health, Finance, Learning, Shopping, Home, Social, Other

Users can assign categories when creating/editing tasks and filter task list by category.

---

## Version Management

**Location:** `app/build.gradle.kts` (since Phase 4.5.3 Kotlin migration)

**Phase-based Versioning Scheme:**
- **0.0.x - 0.1.x** = Phase 0 (Foundation - Update & Logging)
- **0.2.x** = Phase 1 (Taskmaster Foundation & Database)
- **0.3.x** = Phase 2-4 (Core Features Development) ← CURRENT
- **0.4.x** = Phase 5 (Intelligent Planning)
- **0.5.x** = Phase 6 (Widget & Polish)
- **1.0.0** = Taskmaster MVP Release

**Current Version:** v0.3.40 (Build 340)

**How to Update:**
1. Open `app/build.gradle.kts`
2. Increment `versionCode` (integer, sequential: 340 → 341)
3. Update `versionName` (semantic: "0.3.40" → "0.3.41")
4. Commit and push - GitHub Actions creates release with tag `v{versionName}`

**Note:** AndroidManifest.xml no longer contains version info (moved to build.gradle.kts in Phase 4.5.3)

---

## Accessing Logs

**Primary Method: HTTP Server on localhost:8080**

The app runs a SimpleHttpServer that provides direct access to in-memory logs.

```bash
# Get all logs (use this!)
curl http://localhost:8080/logs

# Save logs to file
curl http://localhost:8080/logs > ~/secretary_logs.txt

# View last 20 lines
curl -s http://localhost:8080/logs | tail -20

# Search for errors
curl -s http://localhost:8080/logs | grep ERROR

# Live monitoring (refresh every 2 seconds)
while true; do clear; curl -s http://localhost:8080/logs | tail -20; sleep 2; done
```

**Helper Script:** `~/secretary_log_access.sh` (if available)
```bash
./secretary_log_access.sh logs    # Get all logs
./secretary_log_access.sh latest  # Last 10 entries
./secretary_log_access.sh errors  # ERROR logs only
./secretary_log_access.sh watch   # Live monitoring
```

**When to Read Logs:**
- After crashes: See error stack traces and last operations
- During development: Monitor feature implementation in real-time
- Before commits: Verify changes work correctly
- Debugging: Understand actual execution flow vs expected

**Technical Details:**
- Server starts automatically in MainActivity
- Buffer: 500 log entries max (auto-trimmed)
- Format: Plain text, one entry per line
- Port: 8080 (localhost only, no external access)

---

## Testing & Debugging

### Install and Launch App

```bash
# Install APK (replace or reinstall)
pm install -r ~/storage/downloads/AISecretary-signed.apk

# Launch app
am start -n com.secretary.helloworld/.MainActivity

# Check installed version
pm dump com.secretary.helloworld | grep -E "versionCode|versionName"
```

### View Logcat

```bash
# Real-time filtering by package
logcat | grep Secretary

# Filter by specific classes
logcat | grep -E "(MainActivity|TaskActivity|TaskDatabaseHelper)"

# Check for errors
logcat | grep -E "ERROR|Exception|FATAL"

# Clear logcat before testing
logcat -c
```

### Common Debugging Scenarios

**Task not appearing in list:**
- Check logs for database insertion errors
- Verify filters aren't hiding the task (check status/priority/category filters)
- Confirm task creation dialog saved all fields

**Recurrence not working:**
- Check logs for recurrence logic execution
- Verify recurrence fields are set correctly in database
- Test both INTERVAL and FREQUENCY types separately

**App crashes on launch:**
- Check logcat for stack trace
- Look for database migration errors
- Verify all required resources are compiled in GitHub Actions workflow

**Update system not working:**
- Check internet permission in manifest
- Verify GitHub repository is public (no token needed)
- Check logs for GitHub API response

---

## Development Workflow

### Standard Feature Development

1. **Plan:** Review ROADMAP.md for current phase and tasks
2. **Read Logs:** Check app state before making changes
3. **Code:** Make changes to Java files and resources
4. **Update Workflow:** If adding new files, update `.github/workflows/build-and-release.yml`
5. **Test Locally:** Quick validation with `./build.sh` (if feasible)
6. **Commit:** Descriptive commit message
7. **Push:** Triggers GitHub Actions build
8. **Monitor Build:** Use `gh run list` and `gh run view`
9. **Download & Install:** Test full build on device
10. **Verify Logs:** Confirm feature works as expected

### Adding New Java/Kotlin Class

1. Create file in appropriate package (Clean Architecture):
   - Presentation: `app/src/main/java/com/secretary/helloworld/features/[feature]/presentation/`
   - Domain: `app/src/main/java/com/secretary/helloworld/features/[feature]/domain/`
   - Data: `app/src/main/java/com/secretary/helloworld/features/[feature]/data/`
   - Core: `app/src/main/java/com/secretary/helloworld/core/[subsystem]/`

2. Gradle automatically compiles all files in `app/src/main/java/` - no workflow update needed!

3. Import in other classes: `import com.secretary.helloworld.[package].YourClass`

4. Package name is `com.secretary.helloworld` (legacy, but consistent)

### Adding New Layout Resource

1. Create file in `app/src/main/res/layout/your_layout.xml`

2. Gradle automatically compiles all resources - no workflow update needed!

3. Reference in Java/Kotlin: `R.layout.your_layout`

### Database Schema Changes (Phase 4.5.3+ with Room)

1. Update entity class (e.g., `TaskEntity.kt`)
   ```kotlin
   @Entity(tableName = "tasks")
   data class TaskEntity(
       @ColumnInfo(name = "your_new_column") val yourNewColumn: String? = null
   )
   ```

2. Increment database version in `AppDatabase.kt`
   ```kotlin
   @Database(entities = [TaskEntity::class], version = NEW_VERSION)
   ```

3. Add Room migration
   ```kotlin
   val MIGRATION_4_5 = object : Migration(4, 5) {
       override fun migrate(database: SupportSQLiteDatabase) {
           database.execSQL("ALTER TABLE tasks ADD COLUMN your_new_column TEXT")
       }
   }
   ```

4. Test with existing database (migration runs automatically)

---

## Code Style & Conventions

### Current Standards
- **Language:** Kotlin 1.9.22 (migrating from Java in Phase 4.5.3, hybrid codebase)
- **Architecture:** Clean Architecture (Presentation → Domain → Data)
- **Comments:** Primarily English (some legacy German comments)
- **Logging:** Always use `AppLogger.info/debug/error(TAG, message)` (Kotlin object)
- **Package:** `com.secretary.helloworld` (legacy name, but consistent)

### Logging Best Practices

**Kotlin (preferred):**
```kotlin
companion object {
    private const val TAG = "YourClassName"
}

// Usage (AppLogger is a singleton object):
AppLogger.info(TAG, "Operation started")
AppLogger.debug(TAG, "Variable value: $value")
AppLogger.error(TAG, "Operation failed", exception)
```

**Java (legacy):**
```java
private static final String TAG = "YourClassName";

// Usage:
AppLogger.getInstance(context).info(TAG, "Operation started");
AppLogger.getInstance(context).debug(TAG, "Variable value: " + value);
AppLogger.getInstance(context).error(TAG, "Operation failed", exception);
```

### Kotlin/Java Compilation
- **Kotlin:** 1.9.22 with JVM target 17
- **Java:** Source/Target 17 (for Gradle compatibility)
- **Build System:** Gradle 8.2 with Android Gradle Plugin 8.2.2
- **External Libraries:** Now supported! (Room, Coroutines, AndroidX, etc.)

---

## Git & GitHub

### Commit Workflow

```bash
git status                                    # Check changes
git add .                                     # Stage all
git commit -m "feat: description"            # Commit
git push origin main                          # Push (triggers build)
```

### Commit Message Format
- Use descriptive messages (appears in release notes)
- Prefixes: `feat:`, `fix:`, `refactor:`, `docs:`
- Examples:
  - `feat: add category filter to task list`
  - `fix: resolve database migration crash`
  - `refactor: extract task dialog logic to helper class`

### Viewing Build Status

```bash
# List recent builds
export GH_TOKEN=$(cat ~/.github_token)
gh run list --limit 5

# View specific build logs
gh run view <run-id> --log

# View failed build logs
gh run view <run-id> --log-failed

# Watch current build
gh run watch
```

### Release Management

GitHub Actions automatically:
1. Builds APK from source
2. Extracts version from AndroidManifest.xml
3. Creates GitHub Release with tag `v{versionName}`
4. Uploads `AISecretary-signed.apk` as release asset
5. Includes commit message in release notes

---

## Roadmap & Feature Development

**See ROADMAP.md for complete details.**

### Completed Phases

**Phase 0: Foundation Systems** ✅
- Auto-update system via GitHub Releases
- Logging system with HTTP server access

**Phase 1: Taskmaster Foundation** ✅
- Task entity with all basic fields
- SQLite database with migrations
- Task Activity UI with CRUD operations

**Phase 2: Core Task Management** ✅
- Recurrence system (INTERVAL and FREQUENCY types)
- Smart completion logic
- Task edit functionality
- Search and filter system
- Categories (10 predefined)

**Phase 3: Tracking & Analytics** ✅
- Completions table for historical data
- TaskStatistics class for analytics

**Phase 4: Motivation & Statistics** 🚧 30%
- ✅ Streak tracking (current and longest)
- ⏳ Visual motivation features (pending)
- ⏳ Daily completion stats (pending)

### Next Steps

**Immediate (Phase 4 Completion):**
1. Visual motivation features (progress bars, achievement badges)
2. Daily/weekly completion statistics display
3. Streak visualization in UI
4. Motivational messages/encouragement system

**Future Phases:**
- **Phase 5:** Intelligent planning algorithm
- **Phase 6:** Home screen widget, UI polish, app icon
- **Phase 7:** Public release preparation (if desired)

---

## Known Issues & Technical Debt

### Active Issues

1. **Package Name Inconsistency**
   - Manifest: `com.secretary.helloworld`
   - Should be: `com.secretary`
   - Impact: Awkward imports and naming
   - Fix: Requires full refactor of package structure

2. **No External Libraries**
   - Limitation: Cannot use Room, WorkManager, Material Components, etc.
   - Cause: Termux build process can't handle AAR dependencies
   - Workaround: Manual implementations or wait for Kotlin migration

3. **Memory Leaks (Potential)**
   - UpdateInstaller: BroadcastReceiver may not unregister if download fails
   - SimpleHttpServer: Long-running background thread
   - Recommendation: Add lifecycle management and proper cleanup

4. **No Unit Tests**
   - Risk: Regressions when refactoring
   - Challenge: Testing framework setup in Termux
   - Priority: Medium (for post-MVP)

### Architecture Considerations

**Current:** Single Activity with Dialogs (simple, lightweight)
**Future:** Consider multi-Activity or Fragment-based navigation for scalability

**Current:** SQLite with manual queries
**Future:** Migrate to Room ORM when Kotlin migration happens

**Current:** Java 8
**Future:** Kotlin with Coroutines, Flow, modern architecture

---

## Important Notes for Claude Code

### Required Patterns

1. **Always use AppLogger for logging** - enables monitoring and debugging
2. **Update GitHub Actions workflow** when adding files - critical for builds
3. **Test version increment** before pushing - avoids duplicate version conflicts
4. **Read logs before and after changes** - understand impact of modifications
5. **Check ROADMAP.md** before feature work - ensure alignment with plan

### Protected Sections

Do NOT modify without explicit user approval:
- Feature specifications in ROADMAP.md
- Database schema (requires careful migration)
- Version numbering scheme
- GitHub Actions workflow (unless adding resources)

**Always ask first** before changing:
- Project architecture decisions
- Feature priorities or scope
- Database structure
- Build process

### Common Termux Commands

```bash
# Package info
pkg list-installed | grep android
pkg search <package>
pkg install <package>

# File permissions
chmod +x build.sh

# APK analysis
aapt dump badging app_signed.apk
apksigner verify -v app_signed.apk

# Environment
echo $PREFIX                      # /data/data/com.termux/files/usr
echo $ANDROID_SDK_ROOT           # Android SDK location
```

---

## Quick Reference

### Common Development Commands

```bash
# Read logs
curl http://localhost:8080/logs

# Build (local test only)
cd ~/AI-Secretary-latest
./build.sh

# Commit and deploy
git add . && git commit -m "feat: description" && git push origin main

# Monitor build
export GH_TOKEN=$(cat ~/.github_token)
gh run watch

# Download and install
VERSION="0.3.26"  # Update this
gh release download "v$VERSION" -p "AISecretary-signed.apk" -D ~/storage/downloads/
cd ~/storage/downloads && termux-media-scan AISecretary-signed.apk && termux-open AISecretary-signed.apk

# Check installed version
pm dump com.secretary.helloworld | grep version

# View live logs
logcat | grep Secretary
```

### File Paths

- **Project:** `~/AI-Secretary-latest/`
- **Source:** `~/AI-Secretary-latest/app/src/main/java/com/secretary/helloworld/`
- **Resources:** `~/AI-Secretary-latest/app/src/main/res/`
- **Manifest:** `~/AI-Secretary-latest/app/src/main/AndroidManifest.xml`
- **Build Config:** `~/AI-Secretary-latest/app/build.gradle.kts`
- **GitHub Token:** `~/.github_token`
- **Downloads:** `~/storage/downloads/`

---

## Related Documentation

### Main Documentation Files

- **[README.md](./README.md)** - Project overview, quick start guide, and user-facing documentation
- **[ROADMAP.md](./ROADMAP.md)** - Detailed feature roadmap, technical debt, and development phases
- **[~/CLAUDE.md](../CLAUDE.md)** - Home directory guide for Termux environment (beginner-friendly entry point)

### System Documentation

- **[docs/LOGGING_SYSTEM.md](./docs/LOGGING_SYSTEM.md)** - HTTP logging system (localhost:8080)
  - AppLogger.java - In-memory logging with auto-trimming
  - SimpleHttpServer.java - HTTP server for log access
  - Usage patterns, debugging workflows, troubleshooting

- **[docs/UPDATE_SYSTEM.md](./docs/UPDATE_SYSTEM.md)** - Auto-update mechanism
  - UpdateChecker.java - GitHub Releases API integration
  - UpdateInstaller.java - APK download and installation
  - Version management, GitHub Actions workflow

### Source Code Reference

**Core Components:**
- Task Management: [`src/com/secretary/Task.java`](./src/com/secretary/Task.java), [`TaskDatabaseHelper.java`](./src/com/secretary/TaskDatabaseHelper.java), [`TaskActivity.java`](./src/com/secretary/TaskActivity.java)
- Logging: [`src/com/secretary/AppLogger.java`](./src/com/secretary/AppLogger.java), [`SimpleHttpServer.java`](./src/com/secretary/SimpleHttpServer.java)
- Updates: [`src/com/secretary/UpdateChecker.java`](./src/com/secretary/UpdateChecker.java), [`UpdateInstaller.java`](./src/com/secretary/UpdateInstaller.java)

**Build Configuration:**
- Local: [`build.sh`](./build.sh) - Limited testing only
- Production: [`.github/workflows/build-and-release.yml`](./.github/workflows/build-and-release.yml) - Full build with GitHub Actions
- Manifest: [`AndroidManifest.xml`](./AndroidManifest.xml) - App configuration and versioning

---

**Last Updated:** 2025-11-14
**Current Version:** v0.3.40 (Build 340)
**Status:** Phase 4.5.3 COMPLETE ✅ - Kotlin Migration + Gradle + Domain Infrastructure (Waves 1-10 complete - 100%)
