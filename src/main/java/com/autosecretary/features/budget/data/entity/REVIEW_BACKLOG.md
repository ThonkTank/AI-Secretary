# Review Backlog — budget/data/entity

## Open Issues

### [consider] `yearMonth` public field breaks its own stated invariant
**File:** `BudgetTransactionEntity.java:101`
**Problem:** The Javadoc says "Do not set directly — use setBookingDate() instead", but the field is `public`, so it can be freely set, silently breaking the `yearMonth`/`bookingDate` sync. Room requires public field access on `@Entity` classes, so this cannot be straightforwardly fixed without migrating to property-style getters/setters with `@Ignore` on the backing field and a `@ColumnInfo` getter — a significant Room refactor.
**Tradeoff:** Low risk in practice (only one write path exists today), but misleading to a future developer. Leaving deferred.
