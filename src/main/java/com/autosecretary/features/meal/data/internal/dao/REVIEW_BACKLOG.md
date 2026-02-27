# Review Backlog — meal/data/internal/dao

## Open Issues

### [consider] Full in-memory scan for range queries
**File:** `BaseCollectionDao.java:54-56`

`findAll(Predicate<T>)` always loads every row in the collection and filters in Java. `StorageMealRepository` uses this for date-range queries on `MEAL_PLANS` and `CONSUMPTION_LOGS`. The current `MealStorage` API has no range-query capability (`findByField` supports only equality), so this is the only viable path today. At larger data volumes this will become a bottleneck.

**Suggested alternative:** No action needed while data volumes are small. If meal data grows substantially, extend `MealStorage` with a range-query method and push filtering into the storage layer. Leave as-is for now.
