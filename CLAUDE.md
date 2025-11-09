# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## 🔒 Projektvision

**WICHTIG:** Sektionen, die mit dem 🔒 Emoji markiert sind, dürfen nur mit ausdrücklicher Erlaubnis des Projektinhabers editiert werden.

---

## 🔒 Gesamtkonzept

AI-Secretary ist als umfassendes Alltags-Planungstool konzipiert. Die App soll Nutzern helfen, ihre täglichen Aufgaben intelligent zu organisieren, zu tracken und zu priorisieren.

### Technische Entscheidung
- **Ursprünglicher Prototyp:** Hybrid-App (Capacitor + Web-Technologien)
- **Aktuelle Entwicklung:** Native Android-App (bessere Performance, System-Integration, kleinere APK)
- Der Hybrid-Prototyp dient als Proof of Concept

---

## 🔒 Feature Suite 1: "Taskmaster"

### Übersicht
Taskmaster ist die erste Feature-Suite der AI-Secretary App und bildet das Kernstück der Aufgabenverwaltung.

### 1. Umfassende Todo-Organisation

#### Task-Typen
- **Einzelne Tasks:** Einmalige Aufgaben
- **Wiederkehrende Tasks:**
  - `x pro y`: z.B. "3 mal pro Woche" (flexible Verteilung innerhalb des Zeitraums)
  - `alle x y`: z.B. "alle 2 Tage" (festes Intervall)
  - Zu bestimmten Zeitpunkten: z.B. "Jeden Montag 09:00 Uhr"
- **Verkettete Tasks:** Sequenzen wie A → B → C → A (zyklische Abhängigkeiten)

#### Task-Eigenschaften
- **Titel:** Kurze Beschreibung der Aufgabe
- **Beschreibung:** Ausführliche Details (optional)
- **Numerische Priorität:** Zahlenwert zur Priorisierung

### 2. Intelligentes Tracking

Die App trackt folgende Daten pro Task:
- **Erledigungsstatus:** Erledigt / Unerledigt / Überfällig
- **Erledigungs-Häufigkeit:** Wie oft wurde die Aufgabe erledigt?
- **Erledigungs-Historie:** Wann wurde sie die letzten Male erledigt?
- **Wiederholungs-Logik:** Wann wird eine wiederkehrende Aufgabe wieder auf "unerledigt" gesetzt?
- **Überfälligkeit:** Wie lange ist die Aufgabe bereits überfällig?

#### Zusätzliche Tracking-Daten beim Erledigen
- **Benötigte Zeit:** Wie lange hat die Erledigung gedauert?
- **Schwierigkeitsgrad:** Wie einfach/schwer war die Aufgabe? (User-Input)
- **Übliche Erledigungs-Uhrzeiten:** Zu welchen Tageszeiten wird die Aufgabe typischerweise erledigt?

### 3. Motivations-Features

#### Streak-Tracking
- Anzeige von "Streaks": Wie oft wurde eine Aufgabe rechtzeitig in Folge erledigt?
- Visualisierung der Streak-Länge

#### Statistiken
- **Heute:** Anzahl erledigter Aufgaben heute
- **Letzte 7 Tage:** Anzahl erledigter Aufgaben in den letzten 7 Tagen
- **Durchschnitt (7 Tage):** Durchschnittliche Anzahl erledigter Aufgaben pro Tag (letzten 7 Tage)

### 4. UI/UX-Features

#### App-Ansichten
- **Nächste Aufgabe:** Prominent angezeigte, wichtigste nächste Aufgabe
- **Aufgaben für heute:** Liste aller für heute geplanten Aufgaben
- **Statistik-Dashboard:** Streaks und Erledigungs-Statistiken

#### Home-Screen Widget
Das Widget zeigt auf dem Android-Homescreen:
- Nächste Aufgabe
- Aufgaben für heute
- Streak- und Statistik-Zusammenfassung

### 5. Intelligente Tagesplanung

Basierend auf allen gesammelten Daten soll die App einen intelligenten Tagesplan generieren:

#### Berücksichtigte Faktoren
- **Priorität:** Numerische Task-Priorität
- **Fälligkeit:** Überfällige und heute fällige Tasks
- **Geschätzte Dauer:** Basierend auf historischen Zeiterfassungen
- **Übliche Erledigungs-Zeit:** Zeitpunkt, zu dem die Aufgabe normalerweise erledigt wird
- **Schwierigkeitsgrad:** Historische Schwierigkeits-Bewertungen
- **Verkettungen:** Abhängigkeiten zwischen Tasks

#### Ziel
Automatische, intelligente Sortierung und Vorschläge für einen optimalen Tagesablauf.

---

## Project Overview

AI Secretary is a native Android task management app implementing the Taskmaster feature suite (see above). The app is built in Java using traditional Android XML layouts and a custom SQLite implementation (no Room).

**Key Characteristics:**
- Native Android app targeting SDK 23-35 (Android 6.0 to 15)
- ~34 Java classes, ~8,800 lines of code
- Custom MVVM-inspired architecture using Repository pattern
- GitHub Actions CI/CD for builds (Gradle doesn't work in Termux due to JVM libiconv issues)
- Primary development environment: Termux on Android

## Build System

### Critical: Local Gradle Builds Don't Work in Termux

**Problem:** Gradle fails in Termux with JVM libiconv errors on ARM64.

**Solution:** Use GitHub Actions for all builds:

```bash
# Make code changes, then push to trigger automatic build
git add .
git commit -m "Your changes"
git push origin main

# APK will be available in GitHub Actions → Artifacts after ~5-10 minutes
```

**Workflow:** `.github/workflows/android-build.yml`
- Builds debug APK on every push to main/develop
- Manual release builds via workflow_dispatch
- APKs available as artifacts for 30 days (debug) or 90 days (release)

### Alternative Build Methods
1. **GitHub Actions** (recommended, already configured)
2. **Android Studio** (on desktop/laptop)
3. **AIDE IDE** (Android app with Gradle support - not yet tested)
4. **apkc CLI tool** (not yet tested)

**Never attempt:** `./gradlew` commands in Termux - they will fail.

## Architecture

### Package Structure

```
com.aisecretary.taskmaster/
├── database/           # Data layer
│   ├── TaskEntity.java
│   ├── TaskDao.java
│   ├── CompletionHistoryEntity.java
│   └── CompletionHistoryDao.java
├── repository/         # Business logic layer
│   └── TaskRepository.java (Singleton)
├── utils/              # Manager classes
│   ├── StreakManager.java
│   ├── StatsManager.java
│   ├── RecurrenceManager.java
│   ├── TaskScheduler.java
│   ├── ChainManager.java
│   ├── CategoryManager.java
│   ├── BackupManager.java
│   └── TimeAnalyzer.java
├── adapter/            # RecyclerView adapters
├── fragments/          # UI fragments
├── dialogs/            # Dialog components
├── widget/             # Home screen widgets (3 sizes)
├── service/            # Background services
└── MainActivity.java   # Main UI (533 LOC - needs refactoring)
```

### Key Architectural Patterns

**Repository Pattern:**
- `TaskRepository` is a Singleton that mediates between UI and DAOs
- All database operations go through the repository
- Contains business logic for task completion, streak calculation, etc.

**Manager Classes:**
- Utility classes that handle specific domains (streaks, stats, scheduling)
- Called by repository or activities
- Stateless, pure logic

**Data Layer:**
- Custom SQLite DAOs (not Room)
- **CRITICAL:** Never call `db.close()` in DAOs - causes crashes (fixed in Phase 9.1)
- Thread safety: Repository methods are synchronized

### MVVM Status

**Current State:** Pseudo-MVVM
- Missing: ViewModels and LiveData
- Activities communicate directly with Repository
- No reactive data binding

**Planned:** True MVVM in Phase 11 (Architecture Refactoring)

## Core Features & Implementation

### Task Types
1. **Single Tasks:** One-time tasks with due dates
2. **Recurring Tasks:**
   - "x per y" (e.g., "3 times per week") - flexible scheduling
   - "every x y" (e.g., "every 2 days") - fixed intervals
   - Specific times (e.g., "Every Monday at 9 AM")
3. **Chained Tasks:** Sequential dependencies (A → B → C → A)

**Recurrence Logic:** `RecurrenceManager` + `RecurringTaskService` (background service)

### Intelligent Scheduling

**TaskScheduler** uses multi-factor scoring:
- Task priority (numerical)
- Due date proximity
- Streak risk (tasks close to breaking streaks)
- Historical performance data (completion time, difficulty)
- Chained task dependencies
- Usual completion time of day

### Streak System

**StreakManager** tracks:
- Current streak (consecutive on-time completions)
- Best streak (all-time record)
- Milestone achievements (3, 7, 14, 30, 60, 90, 180, 365 days)

**Display:** MainActivity shows streak stats, widgets display active streaks

### Widgets

Three sizes configured in `res/xml/`:
- 4x4: Large widget with full stats
- 4x2: Medium widget with next task
- 2x2: Compact widget with task count

**Widget Provider:** `TaskWidgetProvider.java`

### Tracking Data

**CompletionHistoryEntity** stores:
- Completion timestamp
- Time spent on task
- Difficulty rating (user input)
- Task state at completion

**CompletionDialog** collects this data when user completes a task.

## Development Workflow

### Making Changes

1. **Edit code** in Termux or Android Studio
2. **Test locally** (if possible) or rely on GitHub Actions build
3. **Commit changes:**
   ```bash
   git add .
   git commit -m "Brief description"
   ```
4. **Push to GitHub:**
   ```bash
   git push origin main
   ```
5. **Wait for build** (~5-10 min)
6. **Download APK** from GitHub Actions → Artifacts
7. **Install on device:**
   ```bash
   # Copy to downloads
   cp app-debug.apk ~/storage/downloads/
   # Open with file manager or
   termux-open ~/storage/downloads/app-debug.apk
   ```

### Testing

**Manual Testing:** See `TEST.md` for comprehensive test plan (200+ test cases)

**Smoke Tests After Build:**
- App launches without crash
- Create task works
- Complete task works
- Widget displays correctly
- Dark mode works
- Statistics screen opens

**Automated Tests:** Currently 0 tests (HIGH PRIORITY - Phase 12)

### Debugging

```bash
# View app logs
adb logcat | grep "com.aisecretary.taskmaster"

# View errors only
adb logcat *:E | grep "Taskmaster"

# Launch app
adb shell am start -n com.aisecretary.taskmaster/.MainActivity

# Force stop
adb shell am force-stop com.aisecretary.taskmaster
```

## Notizen für Claude Code Agenten

- **Geschützte Bereiche:** Alle mit 🔒 markierten Sektionen (am Anfang dieser Datei) nur mit expliziter User-Erlaubnis ändern
- **Feature-Requests:** Neue Features sollten mit dem User diskutiert und in ROADMAP.md ergänzt werden
- **Design-Entscheidungen:** Bei Unklarheiten nachfragen, nicht annehmen
- **When in doubt about feature changes:** Ask the user first

## Known Issues & Technical Debt

### Critical (Must Fix Before Production)
- ⚠️ Widget security: Context injection vulnerability exists
- ⚠️ MainActivity is a "God Object" (533 LOC) - needs fragment breakdown
- ⚠️ No database transactions - race conditions possible in multi-threaded scenarios

### High Priority
- Missing ViewModels (not true MVVM)
- 0% test coverage
- 0% JavaDoc coverage
- ~50+ hardcoded strings (needs i18n)

### Medium Priority
- Dependencies outdated (see ROADMAP.md for specific versions)
- Performance: Main thread blocking in some operations
- Accessibility features missing

**Full Analysis:** Git commit `ea539b7` contains detailed code quality report

## Common Commands

```bash
# Check build status on GitHub
gh run list --limit 5

# View latest build logs
gh run view --log

# Clone repository
git clone https://github.com/ThonkTank/AI-Secretary.git
cd AI-Secretary

# Create feature branch
git checkout -b feature/your-feature-name

# View git status
git status

# View recent commits
git log --oneline -10
```

## File Locations

**Documentation:**
- `CLAUDE.md` - This file (project vision & dev guidance)
- `README.md` - User-facing documentation
- `BUILD_INSTRUCTIONS.md` - Detailed build guide
- `BUILD_FIX_GUIDE.md` - Troubleshooting guide
- `ROADMAP.md` - Development roadmap (currently Phase 9)
- `DESIGN.md` - UX/UI design document
- `TEST.md` - Manual test plan

**Configuration:**
- `app/build.gradle` - App dependencies and build config
- `build.gradle` - Root project config
- `settings.gradle` - Module settings
- `gradle.properties` - Build properties
- `.github/workflows/android-build.yml` - CI/CD pipeline

**Key Source Files:**
- `app/src/main/AndroidManifest.xml` - App manifest
- `app/src/main/java/com/aisecretary/taskmaster/MainActivity.java` - Main UI
- `app/src/main/java/com/aisecretary/taskmaster/repository/TaskRepository.java` - Core business logic
- `app/src/main/res/values/styles.xml` - Material Design themes
- `app/src/main/res/values-night/` - Dark mode styles

## Development Principles

1. **Always use Repository:** Never access DAOs directly from UI
2. **Thread Safety:** Repository methods are synchronized - respect this pattern
3. **No db.close():** Never close database connections in DAOs (causes crashes)
4. **Logging:** Use `android.util.Log` with tag "Taskmaster" for consistency
5. **Material Design:** Follow existing Material theme patterns in styles.xml
6. **Backwards Compatibility:** Min SDK 23 - avoid APIs requiring higher SDK levels
7. **Testing:** If modifying critical logic, update TEST.md with new test cases

## Quick Reference

**Package ID:** `com.aisecretary.taskmaster`
**Min SDK:** 23 (Android 6.0)
**Target SDK:** 35 (Android 15)
**Language:** Java 11
**Database:** SQLite (custom DAOs)
**UI:** XML layouts with Material Design
**Build:** Gradle 8.4 via GitHub Actions

**Repository URL:** https://github.com/ThonkTank/AI-Secretary
**GitHub Actions:** https://github.com/ThonkTank/AI-Secretary/actions
