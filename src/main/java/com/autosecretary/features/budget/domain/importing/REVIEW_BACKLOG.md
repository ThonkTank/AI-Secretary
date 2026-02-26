# Review Backlog — budget/domain/importing

## Open Issues

- [warning] `ImportTransactionRecord.type` is structurally stringly-typed: `TYPE_EXPENSE`/`TYPE_INCOME` bind to `TransactionDirection.name()`, but `TYPE_TRANSFER = "TRANSFER"` has no corresponding enum link (`BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER`). The complete fix is a tri-state `ImportTransactionType` enum (EXPENSE, INCOME, TRANSFER) with a conversion method to `(TransactionDirection, TransactionKind)`, eliminating the raw String entirely — `ImportTransactionRecord.java:12,21-23`, `BudgetTransactionMapper.java:20,51`, `BudgetImportRoomRepository.java:157–163,181–183`.
