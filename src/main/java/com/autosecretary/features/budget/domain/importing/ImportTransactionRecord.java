package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;

public record ImportTransactionRecord(
        String id,
        String accountId,
        String categoryId,
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
