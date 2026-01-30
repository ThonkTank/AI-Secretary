# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Syntax-Check ohne Nebenwirkungen (bevorzugt während der Entwicklung)
./gradlew compileDebugJavaWithJavac

# Build debug APK — ACHTUNG: pushed automatisch zu GitHub!
./gradlew assemble

# Run JUnit/Robolectric unit tests (test sources in test/)
./gradlew testDebugUnitTest

# Clean build
./gradlew clean
```

**WICHTIG — `assemble` pushed automatisch:** `assemble` → `copyToRelease` (APK + version.txt inkrementieren) → `pushToGitHub` (git add release/, commit, push). Für einen reinen Syntax-Check ohne Push immer `compileDebugJavaWithJavac` verwenden.

**WICHTIG:** Nach jeder abgeschlossenen Code-Änderung oder Bugfix MUSS `./gradlew assemble` ausgeführt werden. Das ist der korrekte Abschluss jeder Aufgabe – es baut die APK, inkrementiert die Version und pushed zu GitHub, damit das Auto-Update-System die neue Version an installierte Apps ausliefern kann.

**Typischer Workflow:** Code ändern → `compileDebugJavaWithJavac` (Fehler prüfen) → Fehler fixen → `assemble` (Release).

## Project Layout

Non-standard Android project structure — no `app/` module, sources are at root level:
- Java sources: `src/` (not `app/src/main/java/`)
- Resources: `res/`
- Manifest: `AndroidManifest.xml` (root level)

Configured in `build.gradle.kts` via custom `sourceSets`. Java 17, compileSdk/targetSdk 35, minSdk 26. Uses `coreLibraryDesugaring` for `java.time` API on older devices.

**Flat package names:** Source files use direct package names (e.g. `package activities.inApp;`, `package entities;`) — there is no root `com.autosecretary` prefix in the source. The namespace in `build.gradle.kts` is `com.autosecretary` but that only affects the generated `R` class and manifest merging.

## Architecture

```
src/
├── activities/inApp/     # mainActivity (Launcher, Tab-UI), editItem (Create/Edit Modal)
├── activities/generic/   # UIConstants, ViewHelper, ViewBuilder, taskList (programmatische UI)
├── entities/             # trackedItem, todoList, CalendarEvent, config (DaySchedule pro Wochentag)
├── repository/           # Repo (Interface), SQLrepo, Table, parser/ (itemParser, todoParser)
├── controller/           # todoManager, updateChecker, editorManager
├── data/                 # constants (DB_NAME, DB_VERSION), seedTestData
├── scheduling/           # buildToDo, cleanToDo, CalendarReader,
│                         # DailyPlanningScheduler, DailyPlanningReceiver, BootReceiver
└── test/                 # MockRepo, TestBuildToDo (standalone tests)
```


**Data flow:** `mainActivity` → `todoManager` (Controller) → `buildToDo` (UseCase) → `SQLrepo` (Repository)

**Programmatische UI:** Es gibt keine XML-Layouts. Alle Views werden programmatisch in Java gebaut (`ViewBuilder`, `ViewHelper`, `UIConstants`). Neue UI-Elemente nicht als XML-Layouts anlegen, sondern programmatisch erstellen.

**Background Scheduling:** `DailyPlanningScheduler` registriert AlarmManager-Trigger um 00:00 → `DailyPlanningReceiver` führt aus:
1. `cleanToDo.clean()` — Gestrigen Plan auswerten (Slots → `trackedItem.update()`), veraltete Todos löschen, vergangene scheduled-Daten bereinigen
2. `buildToDo.planWeek()` — Neuen 7-Tage-Plan erstellen
3. `scheduleDaily()` — Nächsten Mitternachts-Alarm registrieren

`BootReceiver` re-registriert den Alarm nach Geräte-Neustart.

## Key Patterns

**trackedItem** is the central entity — Tasks, Goals, Projects, and Blocks all use this class with `ItemType` enum:
- `TASK` — Individual work units with `timeToComplete`, `repetition`, `prefTime`
- `GOAL` — Containers for tasks, have `children` list and time budget
- `BLOCK` — Ordered sequence of goals (e.g., "Stretches" → "Training")
- `PROJECT` — Top-level grouping, enforces `minIntervalDays` between scheduling

**Hierarchy:** Project → Block → Goal → Task

**Repetition Types (RepetitionType enum):**
- `INTERVAL` — "alle X Tage/Wochen" (every X days/weeks)
- `REPS_PER_TIME` — "X mal pro Woche/Monat" (X times per week/month)
- `DAY_OF_TIME` — "jeden Freitag" or "jeden 10." (every Friday / every 10th)

**config** (`config.java`) — Hält `Map<DayOfWeek, DaySchedule>` mit Start-/Endzeit pro Wochentag (z.B. Mo 06:00–18:00). Wird von `buildToDo` genutzt um verfügbare Stunden pro Tag zu bestimmen. `DaySchedule` ist eine innere Klasse mit `start`/`end` (`LocalTime`).

**CalendarEvent** (`CalendarEvent.java`) — Java Record: `record CalendarEvent(String title, LocalTime start, LocalTime end)`. `CalendarReader` liest Device-Kalender via `CalendarContract.Instances`, `CalendarProvider` ist ein Functional Interface für testbare Kalender-Abstraktion.

**Repo Interface** (`Repo.java`) — Abstraktion über SQLrepo, ermöglicht MockRepo für Tests:
```java
public interface Repo {
    <T> T lookup(String table, Map<String, String> filters, String column);   // raw value
    <T> List<T> lookups(String table, Map<String, String> filters, String column); // raw values
    <T> T fetch(Table<T> table, long id);                                     // entity by ID
    <T> T fetch(Table<T> table, Map<String, String> filters);                 // entity by filter
    void write(Object entity);                                                // INSERT or UPDATE
}
```

`lookup`/`lookups` use String table names and return converted primitives. `fetch` uses type-safe `Table<T>` references (`Table.ITEMS`, `Table.TODOS`) and returns entity objects. `write` auto-detects entity type.

## Testing

**Two source sets:**
- `test/` (root level) → Gradle `test` source set for JUnit/Robolectric (`./gradlew testDebugUnitTest`). Derzeit leer.
- `src/test/` → Lives in the `main` source set (weil `java.srcDirs("src")`). Standalone tests with `main()` methods, no Android dependencies.

**MockRepo** (`src/test/MockRepo.java`) implementiert `Repo` mit In-Memory-Maps. Ermöglicht Tests des Scheduling-Algorithmus ohne SQLite/Android.

**Standalone-Tests ausführen:** `TestBuildToDo.java` hat eine `main()`-Methode. Nach `./gradlew compileDebugJavaWithJavac` ausführen mit:
```bash
java -cp build/intermediates/javac/debug/compileDebugJavaWithJavac/classes test.TestBuildToDo
```

## Scheduling Algorithm

**buildToDo** — Globale Slot-Bewertung über alle 7 Tage gleichzeitig:
1. Load/create 7-day plans, sync calendar events
2. Loop: `getItems()` + `prioritize()` → `tryMatch(highest)` globally across all days
3. Verdrängungslogik: Higher-priority items replace lower-priority ones
4. Persist to DB

**Testability:** `buildToDo` nimmt `Repo`-Interface und `CalendarProvider` (Functional Interface) als Dependencies — ermöglicht vollständige Tests ohne Android.

**Priority** basiert auf `Priority` enum (CRITICAL: 100000, HIGH: 400, MODERATE: 200, LOW: 100), plus Overdue-Bonus und Frequency-Multiplier. PrefTime-Matching via logarithmische Score-Funktion.

**Skip conditions in getItems():** `item.blockedDays` enthält den Tag, oder (nur Goal → Project) `parent.blockedDays` enthält den Tag.

**Task vs. Goal Slot-Fitting:** Tasks müssen komplett in einen Slot passen (werden übersprungen wenn zu wenig Zeit). Goals dürfen partiell eingeplant werden (slotCoverage reduziert die Prio proportional).

**blockedDays-Mechanismus:** Jedes Item hat seine **eigenen** blockedDays. `trackedItem.getBlockedDays()` berechnet aus zwei Quellen:
1. Cooldown-Fenster VOR und NACH `lastCompletion` und jedem `scheduled`-Datum (±N Tage)
2. Alle Tage zwischen `lastCompletion` und `calcNextRepetition()` (NICHT für REPS_PER_TIME, da mehrfach pro Periode einplanbar)

`blockedDays` wird automatisch neu berechnet in:
- `update()` — nach Tagesabschluss
- `schedule()` — wenn Item eingeplant wird (+ propagiert scheduled-Datum zum Parent und berechnet Parent-blockedDays neu)
- `unPlan()` — wenn Item verdrängt wird

**Wichtig:** Goals erben NICHT die blockedDays ihres Parents. Stattdessen prüfen `getItems()` und `tryMatch()` separat `parent.blockedDays` (nur für Goal → Project).

## Database

SQLite with four tables:
- `items` — All tracked items (Tasks, Goals, Blocks, Projects)
- `config_schedules` — Day-of-week keyed schedule (start_time, end_time)
- `todos` — Generated daily plans (id, date, start_time, end_time)
- `time_slots` — Nested TimeSlots (todo_id FK, parent_slot_id FK for hierarchy, item_id FK, completed, work_start, work_end)

**Relations in items:** `parent` (single ID), `children` (comma-separated IDs), `followups` ("id:count" pairs, e.g. "5:3,8:1"), `scheduled` (comma-separated ISO dates).

**DB-Strategie (WICHTIG):** Es gibt keine Migrationen. Die App hat genau einen Nutzer (Entwickler) und arbeitet ausschließlich mit geseedeten Testdaten (`seedTestData.java`). Bei Schema-Änderungen:
- `DB_VERSION` in `constants.java` hochzählen
- Schema direkt in `onCreate()` anpassen
- Neue Testdaten in `seedTestData.java` einpflegen
- Die App erkennt den Versions-Wechsel via SharedPreferences und ruft `deleteDatabase()` auf
- **KEINE** `ALTER TABLE`-Migrationen in `onUpgrade()` schreiben

## Auto-Update System

GitHub dient als CDN. `release/version.txt` enthält den aktuellen Integer-versionCode, `release/AutoSecretary.apk` die aktuelle APK. Repository: `ThonkTank/AI-Secretary`.

`updateChecker.java` prüft beim App-Start → fetcht `version.txt` → vergleicht mit lokalem `versionCode` → bei neuer Version: Dialog → Download → FileProvider → System-Installer. Die UI wird erst nach abgeschlossenem Update-Check aufgebaut (Callback-Pattern).

## Language

German comments and variable names are preferred. Documentation can be in German or English.
