package com.autosecretary.features.budget.domain.importing;

import java.time.LocalDate;

public record ParsedTransaction(
        LocalDate bookingDate,
        long amountCents,
        String payee,
        String note,
        String categoryId,
        String importHash
) {
}
