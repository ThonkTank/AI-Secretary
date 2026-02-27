# Review Backlog — budget/data/entity

## Open Issues

### [consider] `yearMonth` public field breaks its own stated invariant
**File:** `BudgetTransactionEntity.java:96`
**Problem:** The Javadoc says "Do not set directly — use setBookingDate() instead", but the field is `public`, so it can be freely set, silently breaking the `yearMonth`/`bookingDate` sync. Room requires public field access on `@Entity` classes, so this cannot be straightforwardly fixed without migrating to property-style getters/setters with `@Ignore` on the backing field and a `@ColumnInfo` getter — a significant Room refactor.
**Tradeoff:** Low risk in practice (only one write path exists today), but misleading to a future developer. Leaving deferred.

### [nit] `direction` column name inconsistent across entities
**File:** `BudgetRecurringTemplateEntity.java:66`
**Problem:** All three budget entities expose a `TransactionDirection direction` field. In `BudgetTransactionEntity` and `BudgetCategory` the column is aliased to `"type"` via `@ColumnInfo`; in `BudgetRecurringTemplateEntity` it is aliased to `"transactionType"`. These are separate tables so there is no runtime failure, but the inconsistency is confusing for anyone writing raw queries against multiple tables.
**Fix suggestion:** Align `BudgetRecurringTemplateEntity` to `@ColumnInfo(name = "type")` with a DB version bump. Because the project uses `fallbackToDestructiveMigration()` this is a data-loss bump — only acceptable at the right moment in development.

### [nit] `recurringValue` carries overloaded meaning depending on `recurringType`
**File:** `BudgetRecurringTemplateEntity.java:76`
**Problem:** The field serves four distinct roles (day-of-month, interval-in-days, unused-zero for WEEKLY, unused-zero for MONTHLY_LAST) with the selection implicit in `recurringType`. The comment documents it correctly, but any code that reads or writes `recurringValue` must be aware of all four interpretations. This is primitive obsession — the correct carrier is a typed sum type or separate named fields.
**Fix suggestion:** Add two nullable typed fields (`Integer monthlyDay` / `Integer intervalDays`) and deprecate `recurringValue`, or use a sealed-class value object in the domain layer and map it in a Room `@TypeConverter`. Either requires a DB schema change; defer until domain layer refactoring.

### [nit] Import-progress counter data clump in `BudgetImportEntity`
**File:** `BudgetImportEntity.java:46-50`
**Problem:** `totalTransactions`, `importedTransactions`, and `autoCategorized` are three related int fields that always move together (they describe a single "import result" concept). New statistics fields will naturally land next to these three and grow the clump.
**Fix suggestion:** Group into an `@Embedded ImportProgress` value object with a column prefix. Requires a DB schema change (column names would be prefixed); defer until next schema revision.

### [nit] Amount stats data clump in `BudgetRecurringTemplateEntity`
**File:** `BudgetRecurringTemplateEntity.java:56-60`
**Problem:** `avgAmountCents`, `minAmountCents`, `maxAmountCents` always move together and form a natural "AmountStats" value object. Any new per-template stat (e.g. stddev) will grow this clump further.
**Fix suggestion:** Group into an `@Embedded AmountStats` value object. Requires a DB schema change (column renames with prefix); defer until next schema revision alongside the import-progress clump fix.
