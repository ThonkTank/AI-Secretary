package com.autosecretary.features.budget.domain.recurring;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A budget transaction enriched with import- and recurring-template linkage fields.
 * <p>
 * Used as input to {@code RecurringPatternDetector.detectPatterns()} — only transactions with
 * {@code isRecurring=false} and {@code isPredicted=false} are eligible for pattern detection.
 */
public record RecurringBudgetTransaction(
        String id,
        String accountId,
        long amountCents,
        LocalDate bookingDate,
        String categoryId,
        String note,
        String payee,
    /** SHA-256-based fingerprint (date + amount + payee) used to deduplicate CSV imports. Null for manually entered transactions. */
        String importHash,
    /** ID of the {@code BudgetImportEntity} record this transaction was created from. Null for manual entries. */
        String importId,
    /**
     * True when this is a forecasted future occurrence that has not yet been booked by the bank.
     * Predicted transactions are excluded from pattern detection (we only analyze confirmed historical data).
     */
        boolean isPredicted,
    /**
     * ID of the recurring template that produced this transaction. Null for manual or unlinked transactions.
     * When non-null and non-blank, this transaction is considered "recurring" (see {@code isRecurring}).
     */
        String parentRecurringId) {

    public RecurringBudgetTransaction {
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(bookingDate, "bookingDate must not be null");
    }

    /**
     * True when this transaction was generated from an active recurring template.
     * Such transactions are already classified as recurring and should be skipped during pattern detection.
     */
    public boolean isRecurring() {
        return parentRecurringId != null && !parentRecurringId.isBlank();
    }

    /** Factory for transactions created during the import pipeline (CSV or PDF). */
    public static RecurringBudgetTransaction forImport(
            String id,
            String accountId,
            long amountCents,
            LocalDate bookingDate,
            String categoryId,
            String note,
            String payee,
            String importHash,
            String importId,
            String parentRecurringId) {
        return new RecurringBudgetTransaction(
                id,
                accountId,
                amountCents,
                bookingDate,
                categoryId,
                note,
                payee,
                importHash,
                importId,
                false,
                parentRecurringId
        );
    }
}
