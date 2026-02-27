# Review Backlog — task/domain

## Open Issues

### [keep] `SchedulingWindowProvider` single-method interface
**File:** `SchedulingWindowProvider.java`
**Observation:** Only one concrete implementation (`TaskScheduleConfigRepository`), but also used as a lambda (`DEFAULT_WINDOW` in `DefaultTaskSlotGenerator`). The SAM interface enables both the concrete implementation and the lambda fallback cleanly. Removing it would force the generator to depend directly on the config repository, violating the domain/application layering. Justified.

### [keep] `TaskSlotGenerator` interface with single implementation
**File:** `TaskSlotGenerator.java`
**Observation:** Only implemented by `DefaultTaskSlotGenerator` in `internal/scheduling/`. The interface sits in the `domain/` package while the implementation is in `domain/internal/scheduling/`. `RegenerateScheduleUseCase` (application layer) depends on the interface, not the internal implementation — this is the project's standard layering convention. Removing it would force the use case to depend on an `internal/` class. Justified.

