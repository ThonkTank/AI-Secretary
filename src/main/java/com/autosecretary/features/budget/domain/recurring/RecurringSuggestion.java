package com.autosecretary.features.budget.domain.recurring;

import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.DayOfWeek;
import java.util.List;

/**
 * A detected recurring transaction suggestion based on historical transaction analysis.
 *
 * <p>See {@code DatePatternDetector.PatternResult} javadoc for how to interpret
 * {@code suggestedValue} and {@code suggestedDayOfWeek} based on {@code suggestedType}.
 *
 * <p>Confidence score (0–1):
 * <ul>
 *   <li>≥ 0.7 — high confidence, suitable for auto-apply without user confirmation</li>
 *   <li>0.4–0.7 — moderate confidence, surface to user for review</li>
 *   <li>&lt; 0.4 — low confidence, typically not surfaced</li>
 * </ul>
 */
public record RecurringSuggestion(
        /** Normalized payee name for deduplication and matching. */
        String normalizedPayee,
        /** Original payee name for UI display. */
        String displayPayee,
        /** Most common category ID in the historical data, or null if uncategorized. */
        String categoryId,
        /** Average transaction amount (in cents). */
        long avgAmountCents,
        /** Minimum transaction amount in the historical data (in cents). */
        long minAmountCents,
        /** Maximum transaction amount in the historical data (in cents). */
        long maxAmountCents,
        /** Transaction direction (INCOME or EXPENSE). */
        TransactionDirection direction,
        /** The detected recurring type (MONTHLY_DAY, WEEKLY, INTERVAL, MONTHLY_LAST). */
        RecurringBudgetTransaction.RecurringType suggestedType,
        /**
         * Type-dependent value: day of month (1–31) for MONTHLY_DAY, interval days for INTERVAL,
         * always 0 for MONTHLY_LAST and WEEKLY (use suggestedDayOfWeek instead).
         */
        int suggestedValue,
        /**
         * Non-null only for WEEKLY recurring type; the day of week on which this transaction occurs.
         * Null for all other types.
         */
        DayOfWeek suggestedDayOfWeek,
        /** IDs of historical transactions used to detect this pattern. */
        List<String> transactionIds,
        /** Confidence score (0–1); higher indicates stronger pattern. */
        double confidenceScore
) {
}
