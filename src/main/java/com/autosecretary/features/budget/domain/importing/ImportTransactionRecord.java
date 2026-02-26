package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;

public record ImportTransactionRecord(
        String id,
        String accountId,
        String categoryId,
        // Raw transaction type from CSV before mapping to the TransactionType enum.
        // Valid values: "EXPENSE", "INCOME", "TRANSFER". Kept as String so the import
        // pipeline can accept and validate arbitrary CSV input before enum conversion.
        String type,
        long amountCents,
        LocalDate bookingDate,
        String yearMonth,
        String note,
        String importHash,
        String payee,
        String importId,
        String templateId
) {
}
