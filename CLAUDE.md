# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Compile Java to bin/
./compile.sh

# Run daily planning algorithm (creates secretary.db with test data)
java -cp "bin:sqlite-jdbc.jar:slf4j-api.jar:slf4j-simple.jar" usecases.daliyPlanning.buildToDo

# Build Android APK (requires Android SDK)
./build.sh
```

**Note:** The package name `daliyPlanning` is intentionally misspelled.

## Architecture

```
src/
├── entities/
│   ├── trackedItem.java       # Central entity for Task/Goal/Project/Block
│   ├── todoList.java          # TimeSlot-based daily schedule container
│   └── config.java            # DayOfWeek → DaySchedule (start/end times)
├── repository/
│   ├── SQLrepo.java           # DB-Zugriff: lookup(), lookups(), fetch(), write()
│   └── parser/
│       ├── itemParser.java    # trackedItem ↔ DB (fromRow, convertRow, toRow)
│       └── todoParser.java    # todoList ↔ DB (fromRow, toRow, loadSlots, persistSlots)
├── controller/
│   └── todoManager.java       # UI-facing: provideList(), completeSlot()
├── data/
│   ├── constants.java         # DB_PATH = "secretary.db"
│   └── seedTestData.java      # Testdaten: 60+ Items über mehrere Goals/Projects
└── usecases/
    ├── daliyPlanning/
    │   ├── buildToDo.java     # 7-Tage Scheduling-Algorithmus (main entry point)
    │   └── cleanToDo.java     # Placeholder
    └── userFlows/
        └── checkToDoItem.java # Stub für Task-Completion
```

## Key Patterns

**trackedItem** is the central entity - Tasks, Goals, Projects, and Blocks all use this class with `ItemType` enum:
- `TASK` - Individual work units with `timeToComplete`, `repetition`, `prefTime`
- `GOAL` - Containers for tasks, have `children` list and time budget
- `BLOCK` - Ordered sequence of goals (e.g., "Stretches" → "Training")
- `PROJECT` - Top-level grouping, enforces `minIntervalDays` between scheduling

**Hierarchy:** Project → Block → Goal → Task

**Repetition Types (RepetitionType enum):**
- `INTERVAL` - "alle X Tage/Wochen" (every X days/weeks)
- `REPS_PER_TIME` - "X mal pro Woche/Monat" (X times per week/month)
- `DAY_OF_TIME` - "jeden Freitag" or "jeden 10." (every Friday / every 10th)

**SQLrepo methods (generisch typisiert):**
```java
// lookup: Erster Treffer oder null
LocalTime start = repo.lookup("config_schedules", filter, "start_time");

// lookups: Alle Treffer als Liste
List<Long> ids = repo.lookups("items", Map.of("type", "Goal"), "id");

// fetch: Entity anhand ID oder Filter
trackedItem item = repo.fetch("items", 5);
todoList list = repo.fetch("todos", Map.of("date", "2026-01-23"));

// write: INSERT oder UPDATE (erkennt Entity-Typ automatisch)
repo.write(myTrackedItem);
repo.write(myTodoList);
```

## Scheduling Algorithm (buildToDo.java)

**Urgency score calculation:**
- Base: Priority enum values (CRITICAL: 100000, HIGH: 400, MODERATE: 200, LOW: 100)
- Plus: priority × (overdue × 0.5) for missed repetitions
- Plus: frequency multiplier for REPS_PER_TIME tasks (normalized 1.0-2.0 based on remaining reps/time)

**Time-preference adjustment (toSlots):**
- If cursor < prefTime: penalty = 1.0 + (timeDiff / 480)
- Adjusted priority = log(priority) × (normalized_diff²) × 100
- Items with matching prefTime get scheduled first at their preferred time

**Berechnungsmethoden auf trackedItem:**
- `frequency()` - repetition interval in days
- `overdue(day)` - missed repetitions count
- `remainingTime(day)` - days left in current period
- `remainingReps(day)` - completions still needed

**Skip conditions in getItems():**
- Parent scheduled: skip if day < max(parent.scheduled)
- Cooldown: skip if day < lastCompletion + cooldown
- NextRepetition: skip if nextRepetition.start > day

## Database

SQLite with four tables:
- `items` - All tracked items (Tasks, Goals, Blocks, Projects)
- `config_schedules` - Day-of-week keyed schedule (start_time, end_time)
- `todos` - Generated daily plans (id, date, start_time, end_time)
- `time_slots` - Nested TimeSlots (todo_id FK, parent_slot_id FK for hierarchy, item_id FK, completed)

**Relations in items:** `parent` (single ID), `children` (comma-separated IDs), `followups` ("id:count" pairs, e.g. "5:3,8:1"), `scheduled` (comma-separated ISO dates).

## Language

German comments and variable names are preferred. Documentation can be in German or English.
