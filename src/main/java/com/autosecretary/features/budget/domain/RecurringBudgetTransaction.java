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
    public String importHash;
    public String importId;

    public boolean isRecurring;
    public boolean isPredicted;
    public String parentRecurringId;
}
