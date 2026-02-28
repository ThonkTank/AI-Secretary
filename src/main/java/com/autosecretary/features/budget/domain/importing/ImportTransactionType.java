package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;

/**
 * Type-safe classification for imported transactions.
 * <p>
 * Note: TRANSFER transactions use direction=EXPENSE and kind=INTERNAL_TRANSFER because transfers
 * are modeled as internal account movements (not external income/expense). The INTERNAL_TRANSFER
 * kind distinguishes them from real expenses.
 * </p>
 */
public enum ImportTransactionType {
    INCOME(TransactionDirection.INCOME, TransactionKind.STANDARD),
    EXPENSE(TransactionDirection.EXPENSE, TransactionKind.STANDARD),
    TRANSFER(TransactionDirection.EXPENSE, TransactionKind.INTERNAL_TRANSFER);

    public final TransactionDirection direction;
    public final TransactionKind kind;

    ImportTransactionType(TransactionDirection direction, TransactionKind kind) {
        this.direction = direction;
        this.kind = kind;
    }

    /**
     * Converts from TransactionDirection to ImportTransactionType.
     * <p>
     * <strong>Important precondition:</strong> This method only handles INCOME and EXPENSE directions.
     * TRANSFER cannot be represented from direction alone and must be used directly.
     * Do not use this method for TRANSFER transactions; set the type to TRANSFER explicitly.
     * </p>
     *
     * @throws IllegalArgumentException if direction is null
     */
    public static ImportTransactionType fromDirection(TransactionDirection direction) {
        if (direction == null) {
            throw new IllegalArgumentException("direction must not be null");
        }
        return direction == TransactionDirection.INCOME ? INCOME : EXPENSE;
    }
}
