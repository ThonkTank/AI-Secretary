package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

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
    public int amountCents;
    public LocalDate transactionDate;
    public String categoryId;
    public String description;
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
}
