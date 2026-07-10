# Budget Domain

This package defines the **domain layer** of the budget feature: repository interfaces, value types,
and pure business logic. It does **not** depend on Android, Room, or any infrastructure — only on
standard Java and the types defined here.

## Package Map

```
budget/domain/
├── BudgetRepository.java          — Primary data contract (accounts, transactions, balances, limits, timeline data)
├── BudgetImportRepository.java    — Import lifecycle + recurring template management
├── TransactionDirection.java      — INCOME / EXPENSE enum with sign-conversion helpers
├── TransactionKind.java           — STANDARD vs. INTERNAL_TRANSFER discriminator
├── CategorySpendSummary.java      — Read projection: category name + amounts spent vs. budget limit
├── MonthlyOverviewItem.java       — Immutable read model: flat transaction row for the monthly overview list
├── AmountParser.java              — Utility: parses bank CSV amount strings into cents
│
├── importing/                     — DTOs for the import pipeline (ParsedTransaction → ImportTransactionRecord)
│   └── README.md                  — Full import pipeline overview and type reference
│
├── recurring/                     — Recurring pattern detection and template scheduling
│   └── README.md                  — Algorithm overview, entry points, and troubleshooting guide
│
└── timeline/                      — Balance timeline reconstruction for the balance chart
    └── README.md                  — Deltas vs. balances explained, reading order
```

## Reading Order

If you're new to this package, read in this order:

1. **`TransactionDirection` and `TransactionKind`** — tiny enums, but used everywhere. Understand them first.
2. **`BudgetRepository`** — the primary data contract; browse the method names to get a feel for what data exists.
3. **`importing/README.md`** — understand how bank statements become persisted transactions.
4. **`recurring/README.md`** — understand how recurring pattern detection works.
5. **`timeline/README.md`** — understand how the balance chart is built.

## Key Design Decisions

### All monetary values are in cents (integers)
`amountCents`, `balanceCents`, `deltaCents` — never `double` or `BigDecimal`. This avoids floating-point
precision issues that are common in financial code.
See [Why not use float for currency?](https://stackoverflow.com/a/3730040).

### Repository interfaces, not concrete classes
`BudgetRepository` and `BudgetImportRepository` are interfaces. Concrete Room implementations live in
`budget/data/repository/`. This lets domain logic stay testable and independent of Android.

### Read projections (`CategorySpendSummary`, `MonthlyOverviewItem`)
These flat records are produced from database query projections and carried upward as immutable
read models. They are not full domain entities — they exist purely to carry data to the UI layer.

## Related Packages

- **`budget/data/`** — Room entities, DAOs, and repository implementations
- **`budget/application/`** — Use-cases that orchestrate domain + data layers
- **`budget/ui/`** — ViewModels and fragments that consume domain types
