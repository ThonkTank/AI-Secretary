package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;

/**
 * Type-safe representation of import transaction classifications.
 * Eliminates string-based type checking and provides explicit conversion to entity kinds.
 */
public enum ImportTransactionType {
    INCOME(TransactionDirection.INCOME, TransactionKind.STANDARD),
    EXPENSE(TransactionDirection.EXPENSE, TransactionKind.STANDARD),
    TRANSFER(TransactionDirection.EXPENSE, TransactionKind.INTERNAL_TRANSFER);

    private final TransactionDirection direction;
    private final TransactionKind kind;

    ImportTransactionType(TransactionDirection direction, TransactionKind kind) {
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
    public TransactionKind toKind() {
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
