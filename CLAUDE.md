# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference

| Task | Command |
|------|---------|
| Syntax check (safe) | `./gradlew compileDebugJavaWithJavac` |
| Build + auto-push | `./gradlew assemble` |
| Run tests | `./gradlew testDebugUnitTest` |
| Widget debug | `adb logcat \| grep -iE "(widget\|RemoteViews\|autosecretary)"` |

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

## Debugging (ADB)

```bash
# Widget-Fehler in Echtzeit anschauen
~/Android/Sdk/platform-tools/adb logcat | grep -iE "(widget|RemoteViews|autosecretary)"

# Logs vor Test löschen, dann Widget neu hinzufügen
~/Android/Sdk/platform-tools/adb logcat -c
```

Typische Widget-Fehler: "Error inflating RemoteViews" + "Class not allowed to be inflated" → View-Typ nicht RemoteViews-kompatibel.

## Project Layout

Non-standard Android project structure — no `app/` module, sources are at root level:
- Java sources: `src/` (not `app/src/main/java/`)
- Resources: `res/`
- Manifest: `AndroidManifest.xml` (root level)

Configured in `build.gradle.kts` via custom `sourceSets`. Java 17, compileSdk/targetSdk 35, minSdk 26. Uses `coreLibraryDesugaring` for `java.time` API on older devices. `applicationId` / `namespace` = `com.autosecretary`.

**Flat package names:** Source files use direct package names (e.g. `package activities.inApp;`, `package entities;`) — there is no root `com.autosecretary` prefix in the source. The namespace `com.autosecretary` only affects the generated `R` class (`import com.autosecretary.R;`) and manifest merging.

**Dependencies:** `androidx.core:core:1.12.0`, `coreLibraryDesugaring` (desugar_jdk_libs 2.1.4). Test: JUnit 4.13.2, Robolectric 4.14.1, `androidx.test:core:1.6.1`.

**versionCode-Mechanismus:** `build.gradle.kts` liest `release/version.txt` beim Build, inkrementiert um 1 und setzt das als `versionCode`. Der neue Wert wird erst während `copyToRelease` zurückgeschrieben — d.h. bei reinem `compileDebugJavaWithJavac` wird die Version nicht verändert.

## Architecture

```
src/
├── activities/inApp/     # mainActivity (Launcher, Tab-UI), editItem (Create/Edit Modal)
├── activities/generic/   # ViewHelper, ViewBuilder, taskList (nutzt TaskRowRenderer)
├── activities/widget/    # TaskWidgetProvider, TaskWidgetFactory, TaskWidgetService, WidgetRefreshApp
├── entities/             # trackedItem, todoList, CalendarEvent, config (DaySchedule pro Wochentag)
├── repository/           # Repo (Interface), SQLrepo, Table, parser/ (itemParser, todoParser)
├── controller/           # todoManager, updateChecker, editorManager
├── data/                 # constants, seedTestData, TaskListData, TaskRowConfig
├── render/               # TaskRowRenderer (einheitlicher Renderer für App + Widget)
├── scheduling/           # buildToDo, cleanToDo, CalendarReader,
│                         # DailyPlanningScheduler, DailyPlanningReceiver, BootReceiver
└── test/                 # MockRepo, TestBuildToDo (standalone tests)
```

**Widget-Architektur:** Kernprinzip: "App passt sich an Widget an" — RemoteViews-kompatible Layouts werden für beide Systeme verwendet.

**RemoteViews-Einschränkungen:** Nur bestimmte Views sind in Widgets erlaubt:
- Layouts: `FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout`
- Views: `TextView`, `ImageView`, `Button`, `ImageButton`, `ProgressBar`, `Chronometer`, `AnalogClock`
- Collections: `ListView`, `GridView`, `StackView`, `AdapterViewFlipper`

**NICHT erlaubt:** `<View>` (für Spacer/Divider stattdessen `ImageView` mit `background` verwenden), `CheckBox` (stattdessen `ImageView` mit Toggle-Icons), custom Views.

**Data flow:** `mainActivity` → `todoManager` (Controller) → `buildToDo` (UseCase) → `SQLrepo` (Repository)

**todoManager** exponiert `provideList()` → `List<TaskEntry>` (Record mit slotId, taskTitle, start/end, completed, goalTitle, progressCurrent/Target/Unit etc.). Wird von der App-UI konsumiert. Callback-Pattern via `TodoListener` Interface. Weitere Methoden:
- `replanToday()` — Plan löschen + neu generieren
- `completeSlot(slotId)` / `uncompleteSlot(slotId)` — Normale Tasks abhaken
- `incrementProgress(slotId)` / `decrementProgress(slotId)` — Progress-Tasks: setzt `slot.progressDelta`, NICHT das Item direkt
- `startTimer(slotId)` / `stopTimer(slotId)` — setzt workStart/workEnd + completes

**editorManager** verwaltet CRUD-Operationen für alle Item-Typen. Exponiert `getAllItems()` → `List<TreeEntry>` (`record TreeEntry(trackedItem item, int depth)`) für die hierarchische Baumdarstellung im Editor (DFS-Traversal). `getAvailableParents(type)` filtert typbasiert: TASK→GOAL, GOAL→PROJECT, PROJECT→null. `createItem()` synct automatisch `parent.children`.

**UI:** Hybrid XML + programmatisch. Hauptstruktur über XML-Layouts:
- Seiten-Layouts: `activity_main.xml`, `view_task_list.xml`, `view_edit_item.xml`, `modal_edit_item.xml`
- Komponenten: `row_tree_item.xml` (Editor-Baum)
- Styling: `res/values/colors.xml`, `dimens.xml`, `styles.xml` — Farben/Größen als Ressourcen statt Hardcoded-Werte

**Einheitliche Layouts** (`res/layout/`): `item_task.xml`, `item_goal_header.xml`, `item_calendar.xml` — RemoteViews-kompatibel (RelativeLayout statt LinearLayout+weight, ImageView statt CheckBox). Widget-Container: `widget_list.xml`. Widget-Metadaten: `res/xml/task_widget_info.xml`.

Farben und Dimensionen liegen ausschließlich in `colors.xml`/`dimens.xml` — programmatischer Zugriff via `ContextCompat.getColor()` und `getResources().getDimension()`. Neue Views bevorzugt als XML-Layout anlegen; dynamische Listeinträge und ähnliches weiterhin programmatisch.

**ViewHelper** (`activities/generic/ViewHelper.java`) — Zwei zentrale Utilities für programmatische UI:
- `dp(Context, int)` — Konvertiert dp zu Pixel (statischer Import überall verwendet)
- `roundedBg(Context, int color, int cornerDp)` — Erstellt `GradientDrawable` mit Farbe und abgerundeten Ecken

**Widget + Unified Renderer:** App und Widget verwenden dieselben Layouts (`item_task.xml`, `item_goal_header.xml`, `item_calendar.xml`). Zwei-Schichten-Architektur:
- `TaskRowConfig.java` — Records (TaskConfig, GoalHeaderConfig, CalendarConfig) mit Factory-Methoden, zentralisiert Business-Logik (Deadline-Rot, Streak-Farbe, etc.)
- `TaskRowRenderer.java` — Überladene `apply*(View, ...)` und `apply*(RemoteViews, ...)` Methoden, wendet Config auf Views an

Widget-Komponenten in `activities/widget/`:
- `TaskWidgetProvider` — AppWidgetProvider, verarbeitet Actions (Toggle, Timer, Refresh, CreateItem)
- `TaskWidgetFactory` — RemoteViewsFactory, liefert RemoteViews für ListView
- `TaskWidgetService` — RemoteViewsService Boilerplate
- `WidgetRefreshApp` — Custom Application, registriert Unlock-Receiver für Auto-Refresh

**Widget-Header-Buttons:** Neben dem App-Titel befinden sich zwei Buttons:
- "+" Button → Öffnet App direkt im Create-Modal (via `ACTION_CREATE_ITEM` Intent → `mainActivity.handleWidgetIntent()`)
- "↻" Button → Refresht Widget-Daten (via `ACTION_REFRESH` Broadcast)

**Completion-Feedback:**
- **App** (`taskList.java`): `animateCompletion()` — Checkbox-Bounce (scale 1.0→1.3→1.0) + Hintergrund-Flash (`completion_flash` → `surface_complete`)
- **Widget** (`TaskWidgetProvider`/`TaskWidgetFactory`): Flash via statischer `flashingSlotId` — bei Completion wird Slot-ID gesetzt, Factory rendert mit `completion_flash`, nach 300ms wird ID gelöscht und Widget neu gerendert

**Meta-Row Badges:** Unterhalb des Task-Titels zeigt `task_meta_row` (LinearLayout) drei optionale Badges:
- Streak: "🔥 X" mit Rarity-Farbe (aus `TaskListData.getStreakRarityColorRes()`)
- Deadline: "Fällig: dd.MM.yyyy" (rot wenn überfällig)
- Remaining: "⏱ X Tage" — aus `trackedItem.remainingTime(today)`

**Background Scheduling:** `DailyPlanningScheduler` registriert AlarmManager-Trigger um 00:00 → `DailyPlanningReceiver` führt aus:
1. `cleanToDo.clean()` — Zwei Phasen: (a) Gestrige Slots auswerten → `trackedItem.update()` mit Slot-Daten + followUp-Tracking, (b) ALLE übrigen Items refreshen → `update(null,...)` für Perioden-Reset, scheduled-Bereinigung, blockedDays-Refresh. Danach alte todoLists aus DB entfernen.
2. `buildToDo.planWeek()` — Neuen 7-Tage-Plan erstellen
3. `scheduleDaily()` — Nächsten Mitternachts-Alarm registrieren
4. `TaskWidgetProvider.notifyWidgetUpdate()` — Widget aktualisieren

`BootReceiver` re-registriert den Alarm nach Geräte-Neustart.

## Key Patterns

**trackedItem** is the central entity — Tasks, Goals, and Projects all use this class with `ItemType` enum:
- `TASK` — Individual work units with `minDurationValue/maxDurationValue`, `timePerProgressUnit`, `repetition`, `prefTime`
- `GOAL` — Containers for tasks, have `children` list and time budget
- `PROJECT` — Top-level grouping, enforces `minIntervalDays` between scheduling

**Hierarchy:** Project → Goal → Task

**Goal-Darstellung:** Goals haben `goalIcon` (Emoji-String, z.B. "💪") und `goalColor` (Hex-String, z.B. "#FFE53935"). Werden als farbige Header mit Icon in Task-Liste und Widget angezeigt. Editierbar im Create/Edit-Modal (Emoji-Eingabe via System-Tastatur + Farb-Grid mit 10 vordefinierten Farben).

**Streak-Rarity-System (MMO-Style):** Tasks zeigen ein farbiges Streak-Badge ("🔥 35") basierend auf Rarity-Stufen:

| Streak  | Rarity    | Farbe                      |
|---------|-----------|----------------------------|
| 0       | —         | Nichts angezeigt           |
| 1–9     | Common    | Grau `rarity_common`       |
| 10–29   | Uncommon  | Grün `rarity_uncommon`     |
| 30–59   | Rare      | Blau `rarity_rare`         |
| 60–99   | Epic      | Lila `rarity_epic`         |
| 100+    | Legendary | Gold `rarity_legendary`    |

Rarity-Farben in `colors.xml`, Streak-Wert kommt aus `trackedItem.currentStreak`. Logik in `TaskListData.getStreakRarityColorRes()`.

**Repetition Types (RepetitionType enum):**
- `NONE` — Einmalig, keine Wiederholung. Nach Completion: `isCompleted = true` permanent. Builder: `.noRepetition()`. Kann optionales `deadline`-Feld haben (siehe Deadline-Logik).
- `INTERVAL` — "alle X Tage/Wochen" (every X days/weeks)
- `REPS_PER_TIME` — "X mal pro Woche/Monat" (X times per week/month)
- `DAY_OF_TIME` — "jeden Freitag" or "jeden 10." (every Friday / every 10th)

**completeFirst-Modus:** Wenn `completeFirst = true`, werden überfällige Tasks NICHT automatisch zurückgesetzt bis sie erledigt sind:
- Streak bleibt erhalten (kein Reset bei Überfälligkeit)
- Perioden-Reset (isCompleted, completions, progressCurrent) wird übersprungen
- Task bleibt einplanbar und wartet auf Completion
- Helper-Methode `isCompleteFirstBlocked(day)` prüft: `completeFirst=true && overdue(day) > 0 && !isCompleted`
- Nach Completion läuft die normale Perioden-Logik wieder an

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

**TaskListData** (`data/TaskListData.java`) — Shared data transformation für App und Widget:
- `DisplayRow` sealed interface mit drei Record-Typen: `GoalHeader`, `TaskItem`, `CalendarEvent`
- `fromEntries(List<TaskEntry>)` transformiert flache TaskEntry-Liste zu DisplayRow-Liste mit eingefügten Goal-Headern
- `getStreakRarityColorRes(int streak)` gibt Rarity-Farb-Resource-ID zurück

**TaskEntry** (`todoManager.java`) — Record für die UI-Datenübergabe:
```java
public record TaskEntry(
    Long slotId,            // TimeSlot ID (zum Abhaken)
    String taskTitle,       // Titel des Tasks
    String taskDescription, // Beschreibung des Tasks
    int slotDuration,       // Berechnete Slot-Dauer in Minuten
    LocalTime start,        // Slot-Startzeit
    LocalTime end,          // Slot-Endzeit
    boolean completed,      // Checkbox-State / "heute erledigt" bei Progress
    String goalTitle,       // Titel des übergeordneten Goals
    Long goalSlotId,        // Goal-Slot ID (für Goal-Completion-Check)
    boolean isCalendarEvent,// Kalender-Termin (nicht abhakbar)
    LocalTime workStart,    // Timer gestartet? (null = nicht gestartet)
    LocalDate deadline,     // Fälligkeitsdatum (null = keine Deadline)
    String goalIcon,        // Emoji-Icon des Goals (z.B. "💪")
    String goalColor,       // Hex-Farbcode des Goals (z.B. "#FFE53935")
    int currentStreak,      // Aktuelle Streak-Länge des Tasks
    int remainingDays,      // Verbleibende Tage (Deadline/Periode)
    int progressCurrent,    // Angezeigter Progress (berechnet je nach progressPerRep-Modus)
    int progressTarget,     // Ziel-Fortschritt (0 = kein Tracking)
    String progressUnit     // Einheit (z.B. "Seiten", nullable)
)
```

## Testing

**Two source sets:**
- `test/` (root level) → Gradle `test` source set for JUnit/Robolectric (`./gradlew testDebugUnitTest`). Verzeichnis existiert noch nicht physisch — Platzhalter für zukünftige Tests.
- `src/test/` → Lives in the `main` source set (weil `java.srcDirs("src")`). Standalone tests with `main()` methods, no Android dependencies.

**MockRepo** (`src/test/MockRepo.java`) implementiert `Repo` mit In-Memory-Maps. Ermöglicht Tests des Scheduling-Algorithmus ohne SQLite/Android.

**Standalone-Tests ausführen:** `TestBuildToDo.java` hat eine `main()`-Methode. Nach `./gradlew compileDebugJavaWithJavac` ausführen mit:
```bash
java -cp build/intermediates/javac/debug/compileDebugJavaWithJavac/classes test.TestBuildToDo
```

## Scheduling Algorithm

**buildToDo** — Globale Slot-Bewertung über alle 7 Tage gleichzeitig:
1. Load/create 7-day plans, sync calendar events
2. Loop: `getItems()` + `buildChains()` → `tryMatchChain(highest)` globally across all days
3. Verdrängungslogik: Higher-priority items replace lower-priority ones
4. Persist to DB

**Chain-basiertes Scheduling:** Items werden zu Ketten gruppiert basierend auf `requiredPredecessor`:
- `buildChains()` gruppiert verkettete Items (A → B → C via requiredPredecessor) zu `TaskChain` Records
- Einzelne Items ohne Kette werden als Ketten der Länge 1 behandelt
- Kette bekommt die **Summe** der Prioritäten aller Mitglieder (nicht max)
- `tryMatchChain()` evaluiert alle **(Startpunkt × Chain-Länge)**-Kombinationen über alle 7 Tage
- `netScore = gainPrio - lossPrio` muss > 0 sein für Platzierung
- **Atomare Chain-Verdrängung:** `TimeSlot.chainId` trackt Zugehörigkeit. Wenn ein Slot verdrängt wird, werden ALLE Slots derselben Chain verdrängt (via `expandToFullChains()`)
- `assignChain()` platziert alle passenden Items konsekutiv und setzt `chainId`
- Gilt für ALLE ItemTypes (Tasks UND Goals können Chains haben)

**Testability:** `buildToDo` nimmt `Repo`-Interface und `CalendarProvider` (Functional Interface) als Dependencies — ermöglicht vollständige Tests ohne Android.

**Priority** basiert auf `Priority` enum (CRITICAL: 100000, HIGH: 400, MODERATE: 200, LOW: 100), plus Overdue-Bonus. PrefTime-Matching via logarithmische Score-Funktion in `tryMatchChain()`. FollowUp-Boost via `scoreFollow()` für historische Muster.

**Prio-Boost (`buildChains()`):** Einheitliche Formel über `item.work(today)` und `item.remainingTime(today)`:
```
basePrio = priority.value + (priority.value × overdue × 0.5)
if (überfällige Deadline): basePrio × 3.0
else if (work > 0 && time > 0): basePrio × min(2.0, 1.0 + work/time)
```
- `work()` = kombinierte verbleibende Arbeit (reps × progress-units, je min 1). Gibt 0 wenn weder Reps/Progress/Deadline aktiv.
- `remainingTime()` = verbleibende Tage (Deadline → Tage bis Deadline; REPS_PER_TIME → Tage in Periode minus eingeplante; Progress-only → 7 Tage Fallback).
- Überfällige Deadline (NONE-Tasks): fester 3.0x Boost, überschreibt die work/time-Formel.

**Progress-Tracking:** Items können `progressCurrent`/`progressTarget`/`progressUnit` haben (z.B. 2/6 Seiten). Zwei Modi:
- **Global** (`progressPerRep=false`, Standard): Progress akkumuliert über Slots hinweg (z.B. "6 Seiten Hausarbeit" — Tag 1: 2 Seiten, Tag 2: weiter bei 2/6)
- **Pro Slot** (`progressPerRep=true`): Jeder Slot startet bei 0 (z.B. "3x pro Woche 5 Liegestütze" — jeder der 3 Slots zeigt 0/5 → 5/5)

**Perioden-Reset:** ALLE wiederkehrenden Tasks mit Progress-Tracking bekommen `progressCurrent=0` am Perioden-Ende (nicht nur progressPerRep). Alter Wert wird in `progressLastPeriod` gesichert.

**Persistenz-Architektur:** Progress-Änderungen werden NICHT direkt im Item gespeichert, sondern im `TimeSlot.progressDelta`:
1. User drückt [+] → `todoManager.incrementProgress()` setzt `slot.progressDelta++` und `slot.completed = true`
2. UI zeigt: Bei `progressPerRep=false`: `item.progressCurrent + slot.progressDelta`. Bei `progressPerRep=true`: nur `slot.progressDelta`
3. Um Mitternacht: `cleanToDo` → `item.update(..., progressDelta, ...)` wendet Delta auf Item an (für Statistik)

**Progress-UI:** Tasks mit Progress zeigen statt Checkbox einen kompakten Stepper `[-] 3/6 [+]`. Hintergrund wird grün bei Fortschritt ("heute erledigt"), Strikethrough erst bei vollem Progress.

**Zeit-pro-Einheit Tracking:** `timePerProgressUnit` speichert den Durchschnitt Minuten pro Fortschrittseinheit (gemessen via Timer oder User-Schätzung). Einheitliche Logik für alle Tasks:
- Mit Progress-Tracking: `Zeit / progressDelta = Min/Einheit`
- Ohne Progress-Tracking: `Zeit / 1 = Min/Completion` (jede Completion = 1 Einheit)
- `getEstimatedTime()` berechnet geschätzte Zeit: `timePerProgressUnit × verbleibende Einheiten` (0 wenn keine Messung vorhanden)

**Flexible Min/Max Duration:** Jedes Item kann separate Min- und Max-Grenzen haben, jeweils in Zeit oder Fortschritts-Einheiten:
- `minDurationValue/Unit` — Minimum pro Tag (z.B. "min 2 Seiten" oder "min 30 min")
- `maxDurationValue/Unit` — Maximum pro Tag (z.B. "max 4 Seiten" oder "max 60 min")
- `DurationUnit` enum: `MINUTES` oder `PROGRESS_UNITS`
- Builder-Convenience-Methoden: `.minMinutes(30)`, `.maxMinutes(60)`, `.minProgress(2)`, `.maxProgress(4)`
- `getMinDurationMinutes()` / `getMaxDurationMinutes()` — Konvertieren zu Minuten (bei PROGRESS_UNITS: Wert × timePerProgressUnit)
- `getSlotDuration()` — Clamps estimated auf [min, max], Fallback auf min oder 30 min wenn keine Schätzung
- Wird von `buildToDo` via `getSlotDuration()` für Slot-Sizing und Scheduling genutzt

Offener Progress (progressCurrent < progressTarget) bewirkt:
- Prio-Boost über `work()/remainingTime()` — mehr verbleibende Einheiten = höherer Boost, gedeckelt bei 2.0x
- Periodenblockierung wird übersprungen (wie REPS_PER_TIME), damit das Item täglich einplanbar bleibt
- `isCompleted` wird auf `false` gehalten solange Progress offen ist

**Deadline-Logik:** Einmalige Tasks (`RepetitionType.NONE`) können ein optionales `deadline`-Feld (`LocalDate`) haben. Wirkung:
- Prio-Boost über `work()/remainingTime()`: je näher die Deadline, desto höher der Multiplier (cap 2.0x). Bei überfälliger Deadline: fester 3.0x Boost.
- Periodenblockierung wird übersprungen (wie REPS_PER_TIME), damit das Item täglich einplanbar bleibt bis zur Erledigung.
- Anzeige in Task-Liste und Widget als "Fällig: dd.MM.yyyy", rot wenn überfällig.
- Edit-UI: DatePickerDialog, nur sichtbar bei TASK + RepetitionType.NONE.

**Skip conditions in getItems():** `item.blockedDays` enthält den Tag, oder (nur Goal → Project) `parent.blockedDays` enthält den Tag.

**Task vs. Goal Slot-Fitting:** Tasks müssen komplett in einen Slot passen (werden übersprungen wenn zu wenig Zeit). Goals dürfen partiell eingeplant werden (slotCoverage reduziert die Prio proportional).

**trackedItem.update()** — Zentraler Entry-Point für Tagesabschluss-Logik. Signatur:
```java
void update(Boolean completed, LocalTime workStart, LocalTime workEnd,
            Long previousItemId, Integer progressDelta, LocalDate day, Repo repo)
```
- `completed=true/false` → Slot-Daten auswerten (completions, prefTime, timePerProgressUnit, streak, followUps)
- `progressDelta` → Fortschrittsänderung aus dem Slot (wird von `incrementProgress`/`decrementProgress` im Slot gesammelt und erst hier angewendet). Auch für Zeit-pro-Einheit Berechnung genutzt.
- `completed=null` → Nur "immer"-Updates (Refresh ohne Slot-Daten)
- "Immer"-Updates (laufen IMMER, auch bei null): Perioden-Reset (`isCompleted` → false wenn neue Periode begonnen), scheduled bereinigen (vergangene Daten entfernen), blockedDays neu berechnen. Bei ALLEN Items mit Progress-Tracking: `progressCurrent` reset auf 0 mit Backup in `progressLastPeriod`.
- `followUps`: Wenn `previousItemId != null`, wird `followUps.merge(previousItemId, 1, Integer::sum)` aufgerufen — trackt welche Items direkt vor diesem erledigt wurden.

**FollowUp-System:** Drei Mechanismen für Task-Reihenfolge:
- **requiredPredecessor** (`trackedItem.requiredPredecessor`): Harte Constraint "dieser Task soll nach Task X kommen". Nur Geschwister-Tasks (selber Parent) wählbar. UI: Spinner im Edit-Modal. **Scheduling:** Items mit requiredPredecessor werden in `buildChains()` zu Ketten gruppiert und als Chain zusammen eingeplant (A → B → C werden konsekutiv platziert). Bei Platzmangel wird die Kette gekürzt.
- **Actual Completion Tracking** (`TimeSlot.previousCompletedItemId`): Trackt die tatsächliche Completion-Reihenfolge (nicht die geplante!). `todoManager` speichert pro Goal-Slot, welcher Task zuletzt erledigt wurde (`lastCompletedByParent`). Bei Completion wird `previousCompletedItemId` im Slot gesetzt. `cleanToDo` liest aus dem Slot statt aus der Iterations-Reihenfolge.
- **FollowUp Prio-Boost** (`trackedItem.scoreFollow(predecessorId)`): Tasks die historisch oft nach bestimmten anderen Tasks erledigt wurden, bekommen einen Planungs-Boost. Berechnung: Wenn Task A mindestens 5x nach Task B erledigt wurde UND das mindestens 5% aller FollowUps von A ausmacht, bekommt A einen Boost = Anteil (z.B. 60% der Follows von B → 60% Boost auf adjustedPrio). Angewendet in `buildToDo.tryMatchChain()` via `findPrecedingItem()`.

**blockedDays-Mechanismus:** Jedes Item hat seine **eigenen** blockedDays. `trackedItem.getBlockedDays()` berechnet aus zwei Quellen:
1. Cooldown-Fenster VOR und NACH `lastCompletion` und jedem `scheduled`-Datum (±N Tage)
2. Alle Tage zwischen `lastCompletion` und `calcNextRepetition()` (NICHT für REPS_PER_TIME, NICHT für Items mit offenem Progress, und NICHT für Items mit Deadline, da diese mehrfach pro Periode einplanbar sind)

`blockedDays` wird automatisch neu berechnet in:
- `update()` — nach Tagesabschluss
- `schedule()` — wenn Item eingeplant wird (+ propagiert scheduled-Datum zum Parent und berechnet Parent-blockedDays neu)
- `unPlan()` — wenn Item verdrängt wird

**Wichtig:** Goals erben NICHT die blockedDays ihres Parents. Stattdessen prüfen `getItems()` und `tryMatch()` separat `parent.blockedDays` (nur für Goal → Project).

## Database

SQLite with four tables:
- `items` — All tracked items (Tasks, Goals, Projects)
- `config_schedules` — Day-of-week keyed schedule (start_time, end_time)
- `todos` — Generated daily plans (id, date, start_time, end_time)
- `time_slots` — Nested TimeSlots (todo_id FK, parent_slot_id FK for hierarchy, item_id FK, completed, work_start, work_end, progress_delta, previous_completed_item_id, chain_id)

**Relations in items:** `parent` (single ID), `children` (comma-separated IDs), `followups` ("id:count" pairs, e.g. "5:3,8:1"), `scheduled` (comma-separated ISO dates), `blocked_days` (comma-separated ISO dates, computed), `required_predecessor` (single ID, optionale Vorgänger-Constraint).

**Darstellung in items:** `goal_icon` (TEXT, Emoji), `goal_color` (TEXT, Hex-Farbcode). Nur für Goals relevant.

**DB-Strategie (WICHTIG):** Es gibt keine Migrationen. Die App hat genau einen Nutzer (Entwickler) und arbeitet ausschließlich mit geseedeten Testdaten (`seedTestData.java`). Bei Schema-Änderungen:
- `DB_VERSION` in `constants.java` hochzählen (aktuell: **19**)
- Schema direkt in `onCreate()` anpassen
- Neue Testdaten in `seedTestData.java` einpflegen
- Die App erkennt den Versions-Wechsel via SharedPreferences und ruft `deleteDatabase()` auf
- **KEINE** `ALTER TABLE`-Migrationen in `onUpgrade()` schreiben

## Auto-Update System

GitHub dient als CDN. `release/version.txt` enthält den aktuellen Integer-versionCode, `release/AutoSecretary.apk` die aktuelle APK. Repository: `ThonkTank/AI-Secretary`.

`updateChecker.java` prüft beim App-Start → fetcht `version.txt` → vergleicht mit lokalem `versionCode` → bei neuer Version: Dialog → Download → FileProvider → System-Installer. Die UI wird erst nach abgeschlossenem Update-Check aufgebaut (Callback-Pattern).

## Language

German documentation, comments and variable names are preferred.
