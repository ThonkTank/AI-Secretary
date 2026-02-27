package com.autosecretary.features.budget.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Budget-Transaktion inkl. Felder für Import- und Recurring-Verknüpfungen.
 */
public class RecurringBudgetTransaction {
    public enum RecurringType {
        MONTHLY_DAY,
        MONTHLY_LAST,
        WEEKLY,
        INTERVAL
    }

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

    /** True when this transaction was generated from an active recurring template (already classified; skip pattern detection). */
    public boolean isRecurring;
    /** True when this is a forecasted future occurrence inserted by {@code synchronizeRecurringTemplateState}; not yet booked by the bank. */
    public boolean isPredicted;
    /** ID of the {@code BudgetRecurringTemplateEntity} that produced this transaction. Null for manual or unlinked transactions. */
    public String parentRecurringId;

    public RecurringBudgetTransaction() {
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
        return tx;
    }
}
