# Task Domain (`features/task/domain`)

## Overview

This package contains the **core domain logic for the task feature**: lifecycle management, completion tracking, tree traversal, and scheduling contracts. It has no Android dependencies — everything here is plain Java.

## Files at a Glance

| File | Role |
|---|---|
| [`TaskLifecycleManager`](TaskLifecycleManager.java) | Stateless service: period advancement, streak tracking, adaptive preferred-time adjustment. Mutates `Task` domain objects. |
| [`TaskCompletionService`](TaskCompletionService.java) | Two-phase task check-off (first tap → STARTED, second tap → COMPLETED). Calls `TaskLifecycleManager` on completion. |
| [`TaskTreeOperations`](TaskTreeOperations.java) | Utility for building a parent-child task tree from a flat list and flattening it back to a list for DB writes. |
| [`TaskCalendarEvent`](TaskCalendarEvent.java) | Thin DTO for Android calendar events, used when explicitly passing calendar data into the scheduler. |

## Subpackages

| Package | Role |
|---|---|
| [`scheduling/`](scheduling/) | **Public scheduling contracts**: interfaces and data types for slot generation (`TaskSlotGenerator`, `SchedulingWindowProvider`, `CalendarBlockedIntervalProvider`, etc.). Start here when working on scheduling. See [`scheduling/README.md`](scheduling/README.md). |
| [`internal/scheduling/`](internal/scheduling/) | **Implementation**: `DefaultTaskSlotGenerator` (the greedy slot-placement algorithm) and `TaskScorer` (multi-layer composite scoring). Not part of the public API — depend on `scheduling/` contracts instead. |

## Recommended Reading Order

1. **[`TaskCompletionService`](TaskCompletionService.java)** — Understand the two-phase check-off first. Short file, clear concept.
2. **[`TaskLifecycleManager`](TaskLifecycleManager.java)** — Understand how periods, streaks, and adaptive times work. Called by `TaskCompletionService` and by `TaskScorer` during maintenance.
3. **[`scheduling/README.md`](scheduling/README.md)** — Then read the scheduling subpackage overview before diving into any scheduling code.

## Key Concepts

### Two-Phase Completion
Checking off a task requires **two taps**:
- Tap 1 → records `realStart` (the user started working); returns `STARTED`
- Tap 2 → records `realEnd`, marks `slot.completed = true`; returns `COMPLETED`

Durations below 3 seconds ("quick tap") or above 24 hours ("stale") are excluded from history tracking.

### Period + Streak Tracking
Tasks with a `repetition` config repeat on a `DAILY / WEEKLY / MONTHLY` period. Each period has a target rep count (`reps`). `TaskLifecycleManager.advancePeriods()` detects when a period has expired and evaluates whether the goal was met (updating streak and carryover debt accordingly).

### Adaptive Preferred Times
When `task.core.adaptive == true`, `TaskLifecycleManager.adaptPrefSlot()` shifts the closest matching `TaskPrefSlot.start` toward the actual completion time using an exponential moving average (α = 0.2, rounded to 5-minute granularity).

### Task Tree
Tasks form a parent-child hierarchy via `TaskRelation`. `TaskTreeOperations` uses `TreeBuilder` (from `util/`) to assemble the flat Room query result into a tree and to flatten it back for bulk writes.

## Related Modules

- **Data layer**: [`features/task/data/`](../../data/) — `Task`, `TaskCore`, `TaskSlot`, `TaskPrefSlot`, `TaskPrerequisite` (Room entities/POJOs)
- **Application layer**: [`features/task/application/`](../../application/) — use cases that orchestrate domain services
- **CLAUDE.md glossary**: Project-wide term definitions (Task, Slot, PrefSlot, Repetition, Period, Streak, Adaptive)
