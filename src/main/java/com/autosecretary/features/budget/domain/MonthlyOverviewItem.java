package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

/**
 * Immutable domain read model for a single row in the monthly budget overview list.
 *
 * Filled by mapping a data-layer Room projection, not by Room itself.
 */
public record MonthlyOverviewItem(
        String transactionId,
        LocalDate bookingDate,
        String yearMonth,
        TransactionDirection direction,
        TransactionKind transactionKind,
        long amountCents,
        String note,
        String accountId,
        String accountName,
        String categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColorHex,
        String linkedTransactionId
) {}
