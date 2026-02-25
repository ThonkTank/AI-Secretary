package com.autosecretary.features.budget.domain;

import java.time.DayOfWeek;
import java.util.List;

/** Vorschlag für ein wiederkehrendes Template auf Basis historischer Buchungen. */
public record RecurringSuggestion(
        String normalizedPayee,
        String displayPayee,
        String categoryId,
        int avgAmountCents,
        int minAmountCents,
        int maxAmountCents,
        RecurringBudgetTransaction.RecurringType suggestedType,
        int suggestedValue,
        DayOfWeek suggestedDayOfWeek,
        List<String> transactionIds,
        double confidenceScore
) {
}
