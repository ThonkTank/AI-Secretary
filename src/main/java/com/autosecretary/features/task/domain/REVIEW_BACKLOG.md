# Review Backlog — task/domain

## Open Issues

### [consider] `SchedulingConflict.reasonCode` is an untyped `String`
**File:** `SchedulingConflict.java:7`, `internal/scheduling/DefaultTaskSlotGenerator.java:39–42`
**What:** Four reason-code constants (`OUTSIDE_WINDOW`, `CALENDAR_OVERLAP`, `PREREQUISITE_BLOCKED`, `NO_MATCHING_GAP`) are private string literals in `DefaultTaskSlotGenerator`. Consumers receive `SchedulingConflict` with a raw `String reasonCode`. Currently no consumer switches on the code, but the `[consider]` applies if scheduling conflict display is ever added to the UI.
**Suggested alternative:** Add a `ReasonCode` enum nested in `SchedulingConflict` or as a standalone type in the domain package. Replace `String reasonCode` with `ReasonCode reasonCode` in the record.

### [keep] `SchedulingWindowProvider` single-method interface
**File:** `SchedulingWindowProvider.java`
**Observation:** Only one concrete implementation (`TaskScheduleConfigRepository`), but also used as a lambda (`DEFAULT_WINDOW` in `DefaultTaskSlotGenerator`). The SAM interface enables both the concrete implementation and the lambda fallback cleanly. Removing it would force the generator to depend directly on the config repository, violating the domain/application layering. Justified.

### [keep] `TaskSlotGenerator` interface with single implementation
**File:** `TaskSlotGenerator.java`
**Observation:** Only implemented by `DefaultTaskSlotGenerator` in `internal/scheduling/`. The interface sits in the `domain/` package while the implementation is in `domain/internal/scheduling/`. `RegenerateScheduleUseCase` (application layer) depends on the interface, not the internal implementation — this is the project's standard layering convention. Removing it would force the use case to depend on an `internal/` class. Justified.
