# Review Backlog — task/domain/scheduling

## Resolved Issues (this run)

- ✅ Fixed typo `TaskTaskTransitionStatLoader` → `TaskTransitionStatLoader` in README.md reading-order section (line 77)

## Resolved Issues (previous run)

All documentation issues identified in the prior run were fixed directly:

- ✅ Added module-level README.md with overview, key concepts, reading order, and common patterns
- ✅ Added comprehensive javadoc to CalendarBlockedIntervalProvider (interface + BlockedInterval record)
- ✅ Added comprehensive javadoc to SchedulingConflict (record + enum reason codes with descriptions)
- ✅ Added comprehensive javadoc to SchedulingWindowProvider (interface + SchedulingWindow record)
- ✅ Enhanced TaskPlanningState class-level javadoc (purpose, lifecycle, invariants, thread-safety)
- ✅ Added method-level javadoc to TaskPlanningState (all public methods, including edge case semantics for minDayDistance)
- ✅ Expanded TaskSlotGenerator javadoc (execution flow, convenience method, parameter descriptions)
- ✅ Added comprehensive javadoc to TaskSlotGenerationResult (semantics, best-effort behavior, use cases)
- ✅ Enhanced TransitionStat javadoc with weight semantics and usage context
