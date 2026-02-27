package com.autosecretary.features.budget.domain.recurring;

import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.DayOfWeek;
import java.util.List;

/** Vorschlag für ein wiederkehrendes Template auf Basis historischer Buchungen. */
public record RecurringSuggestion(
        String normalizedPayee,
        String displayPayee,
        String categoryId,
        long avgAmountCents,
        long minAmountCents,
        long maxAmountCents,
        TransactionDirection direction,
        RecurringBudgetTransaction.RecurringType suggestedType,
        int suggestedValue,
        DayOfWeek suggestedDayOfWeek,
        List<String> transactionIds,
        double confidenceScore
) {
}
