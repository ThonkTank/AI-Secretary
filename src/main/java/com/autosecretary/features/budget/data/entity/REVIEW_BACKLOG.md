# Review Backlog — budget/data/entity

## Open Issues

### [nit] `direction` column name inconsistent across entities @skill:review-architecture
**File:** `BudgetRecurringTemplateEntity.java:97`
**Problem:** All three budget entities expose a `TransactionDirection direction` field. In `BudgetTransactionEntity` and `BudgetCategory` the column is aliased to `"type"` via `@ColumnInfo`; in `BudgetRecurringTemplateEntity` it is aliased to `"transactionType"`. These are separate tables so there is no runtime failure, but the inconsistency is confusing for anyone writing raw queries against multiple tables.
**Fix suggestion:** Align `BudgetRecurringTemplateEntity` to `@ColumnInfo(name = "type")` with a DB version bump. Because the project uses `fallbackToDestructiveMigration()` this is a data-loss bump — only acceptable at the right moment in development.

### [nit] `recurringValue` carries overloaded meaning depending on `recurringType` @skill:review-architecture
**File:** `BudgetRecurringTemplateEntity.java:111`
**Problem:** The field serves four distinct roles (day-of-month, interval-in-days, unused-zero for WEEKLY, unused-zero for MONTHLY_LAST) with the selection implicit in `recurringType`. The comment documents it correctly, but any code that reads or writes `recurringValue` must be aware of all four interpretations. This is primitive obsession — the correct carrier is a typed sum type or separate named fields.
**Fix suggestion:** Add two nullable typed fields (`Integer monthlyDay` / `Integer intervalDays`) and deprecate `recurringValue`, or use a sealed-class value object in the domain layer and map it in a Room `@TypeConverter`. Either requires a DB schema change; defer until domain layer refactoring.

### [nit] Import-progress counter data clump in `BudgetImportEntity` @skill:review-architecture
**File:** `BudgetImportEntity.java:73-77`
**Problem:** `totalTransactions`, `importedTransactions`, and `autoCategorized` are three related int fields that always move together (they describe a single "import result" concept). New statistics fields will naturally land next to these three and grow the clump.
**Fix suggestion:** Group into an `@Embedded ImportProgress` value object with a column prefix. Requires a DB schema change (column names would be prefixed); defer until next schema revision.

### [nit] Amount stats data clump in `BudgetRecurringTemplateEntity` @skill:review-architecture
**File:** `BudgetRecurringTemplateEntity.java:79-91`
**Problem:** `avgAmountCents`, `minAmountCents`, `maxAmountCents` always move together and form a natural "AmountStats" value object. Any new per-template stat (e.g. stddev) will grow this clump further.
**Fix suggestion:** Group into an `@Embedded AmountStats` value object. Requires a DB schema change (column renames with prefix); defer until next schema revision alongside the import-progress clump fix.

---

### [rename] `BudgetAccount`, `BudgetCategory`, `BudgetLimit` — missing `Entity` suffix @skill:review-structure

**Paths involved:** `entity/BudgetAccount.java`, `entity/BudgetCategory.java`, `entity/BudgetLimit.java`

**What makes it hard to navigate today:** Three of the six entity classes lack the `Entity` suffix (`BudgetAccount`, `BudgetCategory`, `BudgetLimit`) while the other three have it (`BudgetTransactionEntity`, `BudgetImportEntity`, `BudgetRecurringTemplateEntity`). When scanning import statements in other files — repositories, application services, UI classes — a reader cannot tell from the type name alone whether `BudgetAccount` is a domain value object or a Room entity. The three with no suffix look like domain models, creating false symmetry with domain types in `budget/domain/`.

**Proposed structural change:** Add `Entity` suffix to all three: `BudgetAccountEntity`, `BudgetCategoryEntity`, `BudgetLimitEntity`. Rename files to match.

**Why it reduces mental load:** Every import of a `*Entity` type unambiguously signals "this is a data-layer Room object". New contributors won't mistake these for domain models. The naming rule becomes one consistent pattern across all six entities.

**Tradeoffs / risks:** High import churn — these three types are referenced in ~25 non-history Java files spanning UI, application, domain, and data layers (Room also registers them via `AppDatabase`). Defer until a convenient maintenance window; fix all three together in a single rename commit.
