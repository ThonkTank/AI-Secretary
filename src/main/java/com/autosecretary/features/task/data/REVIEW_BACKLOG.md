# Review Backlog — features/task/data

## Open Issues

- [nit] TaskPlannedMeal.java:26 — `recipeId` is `long`, inconsistent with the project-wide UUID String PK convention.
- [nit] TaskSlot.java:53-58 — `displacementGroupType` stores one of three string literals ("CHAIN", "FIXED", "SINGLE") with no type safety; should be an enum. Correct fix level is `features/task/domain` or higher (DefaultTaskSlotGenerator is the primary setter).

- [warning] TaskCore.java:92-96 — `Repetition.remainingDays()` calls `LocalDate.now()` directly. Every other scheduling method accepts a reference date as parameter; this is the sole outlier. Makes the method untestable and creates a hidden clock dependency inside a Room entity. Accept a `LocalDate today` parameter instead.
