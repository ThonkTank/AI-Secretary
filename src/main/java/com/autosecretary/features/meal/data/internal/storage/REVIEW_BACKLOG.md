# Review Backlog — meal/data/internal/storage

## Open Issues

### [consider] Interface with only one implementation
**File:** `MealStorage.java`

`MealStorage` has only one implementation (`InMemoryMealStorage`). The indirection may be justified if a Room-backed implementation is planned as a future migration target; leave as-is unless the in-memory approach is confirmed permanent.

### [consider] Non-thread-safe map fields in InMemoryMealStorage
**File:** `InMemoryMealStorage.java:13-14`

`HashMap` and `LinkedHashMap` are not thread-safe. The current wiring in `AppCompositionRoot` passes the storage through `TaskMealIntegrationService` into the task use-case executor (single-threaded), so concurrent access is safe today. However, any future meal UI that calls meal use cases directly from the main thread — bypassing the executor — will introduce silent data corruption without any compile-time or runtime warning.

**Suggested alternative:** When meal UI is built, either route all meal storage calls through the shared executor or switch the map fields to `ConcurrentHashMap`-based equivalents.

## Acknowledged Good Patterns

### [keep] Defensive id injection in upsert
**File:** `InMemoryMealStorage.java:41-43`

`copy.put("id", targetId)` always injects the canonical id into the stored copy regardless of what the caller's `row` map contained. This defensive write prevents stored rows from holding a stale or null id.

### [keep] Counter bumping in getOrGenerateId
**File:** `InMemoryMealStorage.java:60-63`

When an explicit id is provided, the counter is bumped to `max(current, explicitId)` before returning. This ensures future auto-generated ids never collide with any explicitly assigned id, regardless of insertion order.
