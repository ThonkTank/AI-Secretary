# Review Backlog — meal/data/internal

## Open Issues

### [consider] MealStorage interface has only one implementation
- **File**: `storage/MealStorage.java`, `storage/InMemoryMealStorage.java`
- **Why it is more complex than needed**: The `MealStorage` interface (5 methods, 82 lines) exists solely for `InMemoryMealStorage`. The project is feature-complete; no second implementation is expected.
- **Simpler alternative**: Merge the interface into the concrete class; have `BaseCollectionDao` depend directly on `InMemoryMealStorage`.
- **Why deferred**: The interface is small and follows a standard pattern. Removing it saves one file but makes future replacement (e.g., Room-backed storage) harder if it ever happens. Marginal net benefit.
