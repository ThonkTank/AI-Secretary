package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;

/**
 * Type-safe representation of import transaction classifications.
 * Eliminates string-based type checking and provides explicit conversion to entity kinds.
 */
public enum ImportTransactionType {
    INCOME(TransactionDirection.INCOME, BudgetTransactionEntity.TransactionKind.STANDARD),
    EXPENSE(TransactionDirection.EXPENSE, BudgetTransactionEntity.TransactionKind.STANDARD),
    TRANSFER(TransactionDirection.EXPENSE, BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER);

    private final TransactionDirection direction;
    private final BudgetTransactionEntity.TransactionKind kind;

    ImportTransactionType(TransactionDirection direction, BudgetTransactionEntity.TransactionKind kind) {
        this.direction = direction;
        this.kind = kind;
    }

    /**
     * Converts to the appropriate TransactionDirection.
     */
    public TransactionDirection toDirection() {
        return direction;
    }

    /**
     * Converts to the appropriate TransactionKind.
     */
    public BudgetTransactionEntity.TransactionKind toKind() {
        return kind;
    }

    /**
     * Converts from TransactionDirection to ImportTransactionType.
     * Note: TRANSFER is not representable from direction alone; use TRANSFER directly.
     */
    public static ImportTransactionType fromDirection(TransactionDirection direction) {
        return direction == TransactionDirection.INCOME ? INCOME : EXPENSE;
    }
}
