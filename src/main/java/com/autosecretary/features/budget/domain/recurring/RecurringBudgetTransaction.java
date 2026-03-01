package com.autosecretary.features.budget.domain.recurring;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A budget transaction enriched with import- and recurring-template linkage fields.
 * <p>
 * Used as input to {@code RecurringPatternDetector.detectPatterns()} — only transactions with
 * {@code isRecurring=false} and {@code isPredicted=false} are eligible for pattern detection.
 */
public class RecurringBudgetTransaction {
    public String id;
    public String accountId;
    public long amountCents;
    public LocalDate bookingDate;
    public String categoryId;
    public String note;
    public String payee;
    /** SHA-256-based fingerprint (date + amount + payee) used to deduplicate CSV imports. Null for manually entered transactions. */
    public String importHash;
    /** ID of the {@code BudgetImportEntity} record this transaction was created from. Null for manual entries. */
    public String importId;

    /**
     * True when this transaction was generated from an active recurring template.
     * Such transactions are already classified as recurring and should be skipped during pattern detection.
     */
    public boolean isRecurring;
    /**
     * True when this is a forecasted future occurrence that has not yet been booked by the bank.
     * Predicted transactions are excluded from pattern detection (we only analyze confirmed historical data).
     */
    public boolean isPredicted;
    /**
     * ID of the recurring template that produced this transaction. Null for manual or unlinked transactions.
     * When non-null and non-blank, this transaction is considered "recurring" (see {@code isRecurring}).
     */
    public String parentRecurringId;

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
        Objects.requireNonNull(accountId, "accountId must not be null");
        Objects.requireNonNull(bookingDate, "bookingDate must not be null");
        RecurringBudgetTransaction tx = new RecurringBudgetTransaction();
        tx.id = id;
        tx.accountId = accountId;
        tx.amountCents = amountCents;
        tx.bookingDate = bookingDate;
        tx.categoryId = categoryId;
        tx.note = note;
        tx.payee = payee;
        tx.importHash = importHash;
        tx.importId = importId;
        tx.parentRecurringId = parentRecurringId;
        tx.isRecurring = parentRecurringId != null && !parentRecurringId.isBlank();
        tx.isPredicted = false;
        return tx;
    }
}
