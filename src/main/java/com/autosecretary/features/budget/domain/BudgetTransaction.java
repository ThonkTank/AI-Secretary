package com.autosecretary.features.budget.domain;

import java.time.LocalDate;
import java.time.YearMonth;

public record BudgetTransaction(
        String id,
        String accountId,
        String categoryId,
        String templateId,
        TransactionDirection direction,
        TransactionKind transactionKind,
        String linkedTransactionId,
        long amountCents,
        LocalDate bookingDate,
        String yearMonth,
        String note,
        String importHash,
        String payee,
        String importId
) {
    public static BudgetTransaction create(String accountId,
                                           String categoryId,
                                           TransactionDirection direction,
                                           long amountCents,
                                           LocalDate bookingDate,
                                           String note) {
        return new BudgetTransaction(
                null,
                accountId,
                categoryId,
                null,
                direction,
                TransactionKind.STANDARD,
                null,
                amountCents,
                bookingDate,
                YearMonth.from(bookingDate).toString(),
                note,
                null,
                null,
                null
        );
    }
}
