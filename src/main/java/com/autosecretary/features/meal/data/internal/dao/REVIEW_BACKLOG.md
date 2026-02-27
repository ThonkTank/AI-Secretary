# Review Backlog — meal/data/internal/dao

## Open Issues

### [warning] Constructor parameter count + fragile lambda pattern
**File:** `BaseCollectionDao.java:22-32`

Constructor takes 5 parameters, at the threshold for "too many parameters" smell. More problematically, the `idAccessor` and `idSetter` are function-type parameters that callers (StorageMealRepository) must implement as lambdas. This pattern:
- Requires manual lambda creation in each DAO instantiation
- Will multiply if more entity metadata is needed (e.g., versionAccessor, timestampSetter)
- Is fragile: a miswritten lambda (e.g., accessing wrong field) fails silently at runtime

This is a design smell that compounds: every new entity type duplicates the pattern, and future metadata needs will make it worse.

**Suggested fix:** Consider a callback interface (e.g., `EntityIdHandler<T>`) that encapsulates id get/set. Could reduce to 4 constructor params and make ID-handling logic reusable. However, this is a refactoring across BaseCollectionDao + StorageMealRepository — defer unless deemed high-priority.

### [consider] Full in-memory scan for range queries
**File:** `BaseCollectionDao.java:68-70`

`findAll(Predicate<T>)` always loads every row in the collection and filters in Java. `StorageMealRepository` uses this for date-range queries on `MEAL_PLANS` and `CONSUMPTION_LOGS`. The current `MealStorage` API has no range-query capability (`findByField` supports only equality), so this is the only viable path today. At larger data volumes this will become a bottleneck.

**Suggested alternative:** No action needed while data volumes are small. If meal data grows substantially, extend `MealStorage` with a range-query method and push filtering into the storage layer. Leave as-is for now.

## Fixed Issues (this run)

- [nit] Missing parameter validation — Added Objects.requireNonNull() checks to constructor (line 27-31)
- [warning] Undocumented ID generation contract — Added Javadoc to save() method explaining ID generation behavior (line 46-55)
- [nit] Null handling inconsistency — Clarified findById() with explicit if/return structure (line 34-40)
