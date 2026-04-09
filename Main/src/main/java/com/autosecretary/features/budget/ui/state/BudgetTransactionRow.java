package com.autosecretary.features.budget.ui.state;

import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.LocalDate;

/**
 * Immutable UI read model for a single transaction in a transaction list.
 *
 * Flattened representation of a domain BudgetTransactionEntity with all display-ready fields.
 * All monetary amounts are in cents (divide by 100 for display in currency units).
 * categoryColor is pre-resolved from categoryColorHex by the mapping layer.
 */
public record BudgetTransactionRow(
        String transactionId,
        String label,
        TransactionDirection direction,
        String categoryColorHex,
        int categoryColor,
        long amountCents,
        String categoryId,
        String note,
        LocalDate bookingDate,
        String accountId
) {
    /** Sentinel value indicating no category color was set (use default label color). */
    public static final int NO_CATEGORY_COLOR = 0;

    /** Convenience method for UI boolean checks. */
    public boolean isExpense() {
        return direction == TransactionDirection.EXPENSE;
    }
}
