# Task Scheduling Domain (`features/task/domain/scheduling`)

## Overview

This package provides the **public domain contracts** for task slot generation — the core algorithm that converts a list of tasks with constraints into a daily schedule of executable time blocks.

## Key Concepts

### Scheduling Window
A **scheduling window** is the time interval within which tasks can be scheduled on a given day (e.g., 8am to 11pm).

- Contract: [`SchedulingWindowProvider`](SchedulingWindowProvider.java)
- Default: Fixed times for all days (configurable via app settings)
- Used during slot generation to reject placements outside the window with reason `OUTSIDE_WINDOW`

### Blocked Intervals (Calendar)
**Blocked intervals** are calendar events or user-held times that must not overlap with generated task slots (e.g., meetings, breaks, existing events).

- Contract: [`CalendarBlockedIntervalProvider`](CalendarBlockedIntervalProvider.java)
- Source: Device calendar, user-created holds, or a no-op provider (no calendar integration)
- Used during slot generation to reject overlapping placements with reason `CALENDAR_OVERLAP`

> **Related type — [`TaskCalendarEvent`](../TaskCalendarEvent.java):** A UI-facing DTO (title + `LocalTime`
> start/end) used by `TaskCalendarService` when the app needs to display device calendar entries.
> The scheduler itself consumes `CalendarBlockedIntervalProvider.BlockedInterval` so the domain path
> only deals with timestamps, not event titles.

### Scheduling Conflict
A **conflict** represents a constraint violation that prevented a task from being scheduled.

- Type: [`SchedulingConflict`](SchedulingConflict.java)
- Reason codes: [`OUTSIDE_WINDOW`](SchedulingConflict.java#L27-L31) | [`CALENDAR_OVERLAP`](SchedulingConflict.java#L33-L35) | [`PREREQUISITE_BLOCKED`](SchedulingConflict.java#L37-L39) | [`NO_MATCHING_GAP`](SchedulingConflict.java#L41-L43)
- Used for reporting scheduling issues to the user and debugging

### Planning State
**Planning state** tracks the cumulative scheduling decisions made so far, enabling intelligent distribution of tasks across multiple days.

- Type: [`TaskPlanningState`](TaskPlanningState.java)
- Tracks: which days each task was scheduled on, total repetitions placed, minimum spacing
- Lifecycle: created once at the start of multi-day generation (e.g., weekly planning), passed through each day's scheduling call, then discarded
- Purpose: Spread repeated tasks across the week, avoid scheduling too frequently, respect sequence ordering

### Task Slot Generator
The **slot generator** is the main orchestrator that places tasks into time blocks.

- Contract: [`TaskSlotGenerator`](TaskSlotGenerator.java) (public interface)
- Implementation: [`DefaultTaskSlotGenerator`](../internal/scheduling/) (internal; do not depend on this directly)
- Inputs: task list, date range, planning state
- Outputs: created slots + conflicts
- Scoring: composite algorithm (priority, day constraint, preferred time fit, urgency, aging, transition patterns)

### Transition Stats
**Transition stats** are learned patterns (task A → task B) that appear frequently in execution sequences.

- Types: [`TransitionStat`](TransitionStat.java), [`TaskTransitionStatLoader`](TaskTransitionStatLoader.java)
- Purpose: Boost scheduling of tasks that historically follow the current slot, promoting natural workflow patterns
- Optional feature: absent transitions degrade gracefully

### Budget Eligibility
A task's budget requirement is checked during scheduling to gate feasibility.

- Contract: [`TaskBudgetEligibilityService`](TaskBudgetEligibilityService.java)
- Purpose: Prevent scheduling of tasks the user cannot afford
- Example: task requires €50, user has €20 available → ineligible for scheduling
- Separate from booking: actual expense is booked at completion time, not scheduling time

## Reading Order (Novice Path)

1. **[`TaskSlotGenerator.java`](TaskSlotGenerator.java)** — Start here. Understand the public interface, the multi-day workflow, and the result type.

2. **[`SchedulingWindowProvider.java`](SchedulingWindowProvider.java)** — What defines the scheduling time boundary?

3. **[`CalendarBlockedIntervalProvider.java`](CalendarBlockedIntervalProvider.java)** — What constraints are avoided during placement?

4. **[`TaskPlanningState.java`](TaskPlanningState.java)** — How is multi-day state tracked?

5. **[`SchedulingConflict.java`](SchedulingConflict.java)** — What can go wrong during scheduling, and how is it reported?

6. **[`TaskSlotGenerationResult.java`](TaskSlotGenerationResult.java)** — How is success/failure communicated?

7. **[`TransitionStat.java`](TransitionStat.java)** & **[`TaskTransitionStatLoader.java`](TaskTransitionStatLoader.java)** — How do learned patterns improve scheduling?

8. **[`TaskBudgetEligibilityService.java`](TaskBudgetEligibilityService.java)** — How does budget factor into scheduling decisions?

## Architecture & Layering

- **Public contracts** (this package): `TaskSlotGenerator`, `SchedulingWindowProvider`, `CalendarBlockedIntervalProvider`, `TaskTransitionStatLoader`, `TaskBudgetEligibilityService`
- **Implementation details** ([`domain/internal/scheduling/`](../internal/scheduling/)): `DefaultTaskSlotGenerator`, `TaskScorer`
  - Application layer depends on public contracts only
  - Implementation may be swapped/extended without breaking consumers

## Common Patterns

### Generating a Day's Schedule

```java
// Typical use case (from RegenerateScheduleUseCase)
TaskSlotGenerator generator = /* injected */;
TaskPlanningState state = new TaskPlanningState();

// Register existing slots (if any) to avoid duplication
generator.recordPreservedSlots(tasks, today, today.plusDays(1), state);

// Generate for today
TaskSlotGenerationResult result = generator.generateSlotsForDay(tasks, today, state);
System.out.println("Created slots: " + result.createdSlots());
for (SchedulingConflict conflict : result.conflicts()) {
    System.out.println("Conflict: " + conflict.taskId() + " - " + conflict.reasonCode());
}

// Record today's scheduling for tomorrow's awareness
generator.recordScheduledSlotsForDay(tasks, today, state);
```

### Multi-Day Generation

```java
// Use the convenience method for a full week
TaskSlotGenerationResult weekResult = generator.generateSlotsForWindow(
    tasks,
    monday,
    7,  // days
    state
);
```

## Public Resources

- **Java Time API**: https://docs.oracle.com/javase/tutorial/datetime/
- **Calendar integration** (Android): https://developer.android.com/guide/topics/providers/calendar-provider
- **Task scheduling concepts**: This codebase uses a greedy heuristic approach, not a constraint solver (CSP). See `TaskScorer` for the composite scoring function.

## Related Modules

- **Parent**: [`features/task/domain/`](../)
- **Implementation**: [`features/task/domain/internal/scheduling/`](../internal/scheduling/)
- **Application layer consumer**: [`features/task/application/RegenerateScheduleUseCase`](../../application/)
- **Budget integration**: [`features/budget/`](../../budget/)
- **Calendar integration**: [`features/task/application/internal/calendar/`](../../application/internal/calendar/)
