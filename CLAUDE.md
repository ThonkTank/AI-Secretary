# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
./gradlew assembleDebug          # Build debug APK (also copies to release/ and pushes to GitHub)
./gradlew test                   # Run all unit tests (JUnit 4 + Robolectric)
./gradlew testDebugUnitTest      # Run debug unit tests only
```

**Warning:** `assembleDebug` automatically increments `release/version.txt`, copies the APK to `release/`, and **pushes to GitHub** via `git push`.

No tests exist yet — the `test/` source set is configured but empty.

## Project Layout

This is a non-standard Android project using flat source directories (no `app/` module):

| Path | Purpose |
|------|---------|
| `src/` | Active Java source (flat, not `src/main/java/`) |
| `old/` | Legacy code being migrated — do not modify |
| `res/` | Android resources (currently minimal) |
| `AndroidManifest.xml` | Root-level manifest |
| `build.gradle.kts` | Single-module Kotlin DSL build |
| `release/` | Built APK + version counter |

Source set mapping in Gradle: `java.srcDirs("src")`, `res.srcDirs("res")`, `manifest.srcFile("AndroidManifest.xml")`.

## Architecture

**MVVM with Room** — the app is being rebuilt from a legacy SQLite/custom-parser architecture.

```
views/          → Activities + ViewModels (LiveData)
services/       → Business logic (scheduling algorithms)
database/       → Room entities, DAOs, type converters
config/         → SharedPreferences wrappers
constants/      → Enums (Priority, Period)
```

### Data flow
`Preferences` (per-weekday start/end times, default 06:00–16:00) → `MainViewModel` → `SlotGenerator` → `TaskDAO` (Room) → SQLite (`autosecretary.db`). All DB access runs on a background thread via `Executors.newSingleThreadExecutor()`.

`TaskDAO.write()` uses a delete-then-reinsert strategy (not `@Update`) — it deletes the existing core + all related rows, then re-inserts everything.

### Task model
`Task` is a composite Room entity: `TaskCore` (with embedded `Repetition`, `Progress`, `History`) + related tables (`TaskSlot`, `TaskPrefSlot`, `TaskFollowUp`) joined via `@Relation`.

### View model
`ViewTask` flattens the `Task` tree into a list with indent levels for the RecyclerView. `ViewTask.fromTree()` recursively walks the tree and produces a flat `List<ViewTask>`.

### Scoring algorithm
`Task.score()` combines priority, urgency (remaining vs required days), preferred-time fit (8-hour window), and aging factor. Child task priorities influence parent scoring.

### Slot generation
`SlotGenerator` greedily assigns tasks to time slots using composite scores. `TreeBuilder` constructs parent-child hierarchy from flat database records.

## Refactoring Status

The app has three feature domains. Only tasks are actively being rebuilt:

| Feature | Status | Location |
|---------|--------|----------|
| Task scheduling | **Active** — Room + MVVM | `src/` |
| Budget/Finance | Not migrated | `old/controller/budgetTab/`, `old/entities/` |
| Meal planning | Not migrated | `old/controller/mealTab/`, `old/entities/` |

## Key Technical Details

- **Java 17** with core library desugaring (minSdk 26, targetSdk 35)
- **Room 2.6.1** for persistence, annotation processor (not KSP)
- **XML layouts** in `res/layout/` (`activity_main`, `task_row`), displayed via RecyclerView + `TaskRowAdapter`
- **Room DB version 1**, `exportSchema = false` — entity changes require a migration or `fallbackToDestructiveMigration()`
- **Package**: `com.autosecretary`
- **Single Activity** architecture: `views.mainView.MainActivity`
