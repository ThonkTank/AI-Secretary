package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;

public record ImportTransactionRecord(
        String id,
        String accountId,
        String categoryId,
        ImportTransactionType type,
        long amountCents,
        LocalDate bookingDate,
        String note,
        String importHash,
        String payee,
        String importId,
        String templateId
) {}
