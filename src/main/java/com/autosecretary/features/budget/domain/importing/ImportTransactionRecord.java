package com.autosecretary.features.budget.domain.importing;

import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.LocalDate;

public record ImportTransactionRecord(
        String id,
        String accountId,
        String categoryId,
        // Raw transaction type from CSV before mapping to the TransactionDirection enum.
        // Valid values: TYPE_EXPENSE, TYPE_INCOME, TYPE_TRANSFER. Kept as String so the import
        // pipeline can accept and validate arbitrary CSV input before enum conversion.
        String type,
        long amountCents,
        LocalDate bookingDate,
        String note,
        String importHash,
        String payee,
        String importId,
        String templateId
) {
    public static final String TYPE_EXPENSE = TransactionDirection.EXPENSE.name();
    public static final String TYPE_INCOME = TransactionDirection.INCOME.name();
    public static final String TYPE_TRANSFER = "TRANSFER";
}
