# AI Secretary - Task Management App

A native Android task management application with advanced recurring task support, developed entirely in Termux on Android.

**Current Version:** v0.3.43 (Build 343)
**Status:** Phase 4.5.4 (Package Renaming) COMPLETE ✅ | Package simplified from com.secretary.helloworld → com.secretary
**Repository:** https://github.com/ThonkTank/AI-Secretary

---

## What is AI Secretary?

AI Secretary is a personal productivity app focused on the **Taskmaster** feature suite - a comprehensive system for managing daily, weekly, and long-term recurring tasks with intelligent planning and streak tracking.

### Key Features

✅ **Task Management**
- Create, edit, delete, and complete tasks
- Priority levels: Low, Medium, High, Urgent
- 10 predefined categories (Work, Personal, Health, etc.)
- Rich task descriptions and metadata

✅ **Advanced Recurrence System**
- **INTERVAL**: "Every X days/weeks/months" (e.g., "Every 3 days")
- **FREQUENCY**: "X times per period" (e.g., "3 times per week")
- Smart completion logic with automatic task reset
- Support for daily, weekly, monthly, and yearly cycles

✅ **Tracking & Analytics**
- Completion history with detailed metadata
- Streak tracking (current and longest streaks)
- Task statistics and performance metrics
- Time spent and difficulty tracking

✅ **Filtering & Organization**
- Search by title or description
- Filter by status (all/active/completed)
- Filter by priority or category
- Sort by priority, due date, category, or creation date

✅ **Foundation Systems**
- Auto-update via GitHub Releases
- In-app logging system (HTTP server on port 8080)
- Settings with version info and update check

---

## Quick Start

### For Users

**Install the latest version:**

1. Download latest APK from [Releases](https://github.com/ThonkTank/AI-Secretary/releases)
2. Install on your Android device (min API 28 / Android 9)
3. Grant required permissions (Internet for updates)
4. Start managing your tasks!

**Update the app:**

1. Open app → Tap Settings (⚙) → "Check for Updates"
2. Download and install update when available

### For Developers

**Prerequisites:**
- Termux on Android
- Git, GitHub CLI (`gh`)
- Android SDK (API 33) installed
- GitHub Personal Access Token in `~/.github_token`

**Clone and setup:**

```bash
cd ~
git clone https://github.com/ThonkTank/AI-Secretary.git AI-Secretary-latest
cd AI-Secretary-latest
```

**Read the documentation:**

```bash
cat CLAUDE.md     # Complete developer guide (START HERE!)
cat ROADMAP.md    # Feature roadmap and technical debt
cat docs/*.md     # System-specific documentation
```

**Build and deploy:**

```bash
# Production build (REQUIRED - no local Gradle!)
# 1. Update version in AndroidManifest.xml
# 2. Commit and push
git add . && git commit -m "feat: your feature" && git push origin main

# 3. Monitor build
export GH_TOKEN=$(cat ~/.github_token)
gh run watch

# 4. Download and install
VERSION="0.3.26"  # Your new version
gh release download "v$VERSION" -p "AISecretary-signed.apk" -D ~/storage/downloads/
cd ~/storage/downloads && termux-media-scan AISecretary-signed.apk && termux-open AISecretary-signed.apk
```

**Local testing (limited - no external libraries):**

```bash
./build.sh  # Quick test only!
```

---

## Project Structure

```
AI-Secretary-latest/
├── src/com/secretary/          # Java source code (16 files)
│   ├── MainActivity.java           # Landing page with settings
│   ├── TaskActivity.java           # Main task management UI
│   ├── Task.java                   # Task entity with recurrence logic
│   ├── TaskDatabaseHelper.java     # SQLite database (v4, 2 tables)
│   ├── TaskListAdapter.java        # ListView adapter
│   ├── TaskDialogHelper.java       # Dialog management
│   ├── TaskFilterManager.java      # Search/filter/sort logic
│   ├── TaskStatistics.java         # Analytics & streak calculations
│   ├── DatabaseConstants.java      # DB schema constants
│   ├── AppLogger.java              # In-memory logging system
│   ├── SimpleHttpServer.java       # HTTP server for log access
│   ├── LogProvider.java            # ContentProvider (legacy)
│   ├── UpdateChecker.java          # GitHub Releases API client
│   └── UpdateInstaller.java        # APK download manager
│
├── res/                        # Android resources
│   ├── layout/                     # 7 layout files
│   │   ├── activity_main.xml           # Landing page
│   │   ├── activity_tasks.xml          # Task list with filters
│   │   ├── task_list_item.xml          # Individual task item
│   │   ├── dialog_add_task.xml         # Task creation dialog
│   │   ├── dialog_completion.xml       # Completion dialog
│   │   ├── dialog_settings.xml         # Settings dialog
│   │   └── dialog_logs.xml             # Log viewer
│   ├── menu/main_menu.xml          # Action bar menu
│   └── values/strings.xml          # String resources
│
├── docs/                       # Technical documentation
│   ├── LOGGING_SYSTEM.md           # HTTP logging system
│   └── UPDATE_SYSTEM.md            # Auto-update mechanism
│
├── .github/workflows/          # GitHub Actions
│   └── build-and-release.yml       # Production build workflow
│
├── AndroidManifest.xml         # App manifest
├── build.sh                    # Local build script (limited!)
├── CLAUDE.md                   # Developer documentation
├── README.md                   # This file
└── ROADMAP.md                  # Feature roadmap & tech debt
```

---

## Architecture Overview

### Database Schema

**tasks table** (17 columns):
- Basic: id, title, description, category, created_at, due_date, is_completed, priority
- Recurrence: recurrence_type, recurrence_amount, recurrence_unit, last_completed_date, completions_this_period, current_period_start
- Streaks: current_streak, longest_streak, last_streak_date

**completions table** (6 columns):
- completion_id, task_id (FK), completed_at, time_spent, difficulty, notes

### Recurrence Logic

**INTERVAL Type** ("Every X Y"):
- Example: "Every 3 days"
- Task automatically resets X time units after completion
- Due date moves forward by interval
- Implementation: `resetIntervalTask()` in TaskDatabaseHelper

**FREQUENCY Type** ("X times per Y"):
- Example: "3 times per week"
- Tracks completions within current period
- Resets counter at period boundary
- Implementation: `incrementFrequencyProgress()` in TaskDatabaseHelper

### Logging System

**HTTP Server on localhost:8080** (not file-based):
- Starts automatically in MainActivity
- In-memory buffer: 500 entries (auto-trimming)
- Access: `curl http://localhost:8080/logs`
- Endpoints: `/logs` (all logs), `/` (help)

**Purpose:** Enable Claude Code and developers to monitor app behavior in real-time.

---

## Development Workflow

### Standard Feature Development

1. **Plan:** Review `ROADMAP.md` for current phase and priorities
2. **Read Logs:** `curl http://localhost:8080/logs` to understand current state
3. **Code:** Make changes to Java files and resources
4. **Update Workflow:** If adding files, update `.github/workflows/build-and-release.yml`
5. **Version:** Increment versionCode and versionName in `AndroidManifest.xml`
6. **Commit & Push:** Triggers GitHub Actions build
7. **Monitor:** `gh run watch` to track build progress
8. **Download:** `gh release download` to get APK
9. **Install:** Install and test on device
10. **Verify:** Check logs to confirm feature works

### Adding New Components

**New Java class:**
1. Create `src/com/secretary/YourClass.java`
2. Update GitHub Actions workflow (javac step)
3. Import: `import com.secretary.helloworld.YourClass;`

**New layout:**
1. Create `res/layout/your_layout.xml`
2. Update GitHub Actions workflow (aapt2 compile step)
3. Reference: `R.layout.your_layout`

**Database change:**
1. Increment `DATABASE_VERSION` in `DatabaseConstants.java`
2. Add migration in `TaskDatabaseHelper.onUpgrade()`
3. Update CREATE TABLE statement
4. Test with existing database

---

## Technology Stack

- **Language:** Kotlin 1.9.22 (hybrid codebase - migration in progress, Waves 1-10 complete)
- **Database:** Room 2.6.1 with KSP (domain infrastructure created, legacy SQLite v4 still active)
- **Build:** Gradle 8.2 + Android Gradle Plugin 8.2.2 (via GitHub Actions)
- **SDK:** Android API 35 (compile), API 28+ (minSdk)
- **Architecture:** Clean Architecture in progress (Presentation → Domain → Data layers)
  - Domain: Services (RecurrenceService, StreakService) + Repositories (TaskRepository, CompletionRepository)
  - Data: Room DAOs (TaskDao, CompletionDao) + Repository implementations
  - Presentation: Activities + ViewModels (planned Phase 4.5.4)
- **Concurrency:** Kotlin Coroutines + Flow
- **Patterns:** Singleton, Repository Pattern, MVVM (in progress), Clean Architecture

---

## Development Phases

### Completed ✅

**Phase 0: Foundation Systems**
- Auto-update via GitHub Releases
- HTTP logging system with external access

**Phase 1: Taskmaster Foundation**
- Task entity with comprehensive fields
- SQLite database with migrations
- Task Activity UI with CRUD operations

**Phase 2: Core Task Management**
- Recurrence system (INTERVAL and FREQUENCY)
- Smart completion logic
- Task edit functionality
- Search and filter system
- Categories (10 predefined)

**Phase 3: Tracking & Analytics**
- Completions table for historical data
- TaskStatistics class for analytics

### In Progress 🚧

**Phase 4: Motivation & Statistics (30%)**
- ✅ Streak tracking (current and longest)
- ⏳ Visual motivation features (progress bars, badges)
- ⏳ Daily/weekly completion statistics
- ⏳ Motivational messages system

### Planned 📋

**Phase 5: Intelligent Planning**
- AI-powered task scheduling
- Smart reminders and notifications
- Workload balancing

**Phase 6: Widget & Polish**
- Home screen widget
- Custom app icon
- UI/UX refinements
- Performance optimization

---

## Documentation

- **[CLAUDE.md](./CLAUDE.md)** - Complete developer guide (architecture, workflows, debugging)
- **[ROADMAP.md](./ROADMAP.md)** - Detailed feature roadmap and technical debt tracking
- **[docs/LOGGING_SYSTEM.md](./docs/LOGGING_SYSTEM.md)** - HTTP logging system documentation
- **[docs/UPDATE_SYSTEM.md](./docs/UPDATE_SYSTEM.md)** - Auto-update mechanism documentation
- **[~/CLAUDE.md](../CLAUDE.md)** - Home directory guide for Termux environment

---

## Known Limitations

1. **Gradle in Termux** - Gradle works via GitHub Actions only (Termux has JVM libiconv issues)
2. **Hybrid Codebase** - Migration to Kotlin in progress (Waves 1-10 complete, ~70% migrated)
3. **Package Name** - Currently `com.secretary.helloworld` (should be `com.secretary`)
4. **Integration Pending** - Domain infrastructure created but not yet integrated (Phase 4.5.4)
5. **Testing Coverage** - Unit tests planned for Phase 4.5.6 (70%+ target for domain layer)

---

## Contributing

This is a personal project, but contributions are welcome!

1. Read `CLAUDE.md` for full developer documentation
2. Check `ROADMAP.md` for current priorities
3. Follow the development workflow outlined above
4. Test thoroughly before pushing
5. Use descriptive commit messages (appears in release notes)

---

## License

Personal project - no formal license. Use at your own risk.

---

## Device & Environment

- **Developed on:** Google Pixel 8
- **Android Version:** Android 16 (API 36)
- **Development Environment:** Termux (googleplay.2025.10.05)
- **Java:** OpenJDK 21.0.9 (compiles to Java 8 target)
- **Build System:** GitHub Actions (Ubuntu, Android SDK 33)

---

## Support & Contact

- **Issues:** https://github.com/ThonkTank/AI-Secretary/issues
- **Releases:** https://github.com/ThonkTank/AI-Secretary/releases

---

**Last Updated:** 2025-11-14
**Current Version:** v0.3.43 (Build 343)
**Status:** Active development - Phase 4.5.4 (Package Renaming) COMPLETE ✅
