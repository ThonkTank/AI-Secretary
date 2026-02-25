package com.autosecretary.features.budget.domain;

import java.time.DayOfWeek;
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

    public enum RepUnit {
        DAY,
        WEEK,
        MONTH
    }

    public Long id;
    public Long accountId;
    public int amountCents;
    public LocalDate transactionDate;
    public Long categoryId;
    public String description;
    public String payee;
    public String importHash;
    public Long importId;

    public boolean isRecurring;
    public boolean isPredicted;
    public Long parentRecurringId;
    public RecurringType recurringType;
    public int recurringValue;
    public DayOfWeek recurringDayOfWeek;
    public RepUnit recurringUnit;
    public LocalDate nextDue;
    public Integer amountMinCents;
    public Integer amountMaxCents;
    public Integer amountAvgCents;
    public int occurrenceCount;

    public static class Builder {
        private final RecurringBudgetTransaction tx = new RecurringBudgetTransaction();

        public Builder(Long accountId, int amountCents, LocalDate date, Long categoryId) {
            tx.accountId = accountId;
            tx.amountCents = amountCents;
            tx.transactionDate = date;
            tx.categoryId = categoryId;
        }

        public Builder description(String description) {
            tx.description = description;
            return this;
        }

        public Builder payee(String payee) {
            tx.payee = payee;
            return this;
        }

        public Builder importHash(String importHash) {
            tx.importHash = importHash;
            return this;
        }

        public Builder importId(Long importId) {
            tx.importId = importId;
            return this;
        }

        public Builder recurringMonthlyDay(int dayOfMonth) {
            tx.isRecurring = true;
            tx.recurringType = RecurringType.MONTHLY_DAY;
            tx.recurringValue = dayOfMonth;
            return this;
        }

        public Builder recurringMonthlyLast() {
            tx.isRecurring = true;
            tx.recurringType = RecurringType.MONTHLY_LAST;
            return this;
        }

        public Builder recurringWeekly(DayOfWeek dayOfWeek) {
            tx.isRecurring = true;
            tx.recurringType = RecurringType.WEEKLY;
            tx.recurringDayOfWeek = dayOfWeek;
            return this;
        }

        public Builder recurringInterval(int value, RepUnit unit) {
            tx.isRecurring = true;
            tx.recurringType = RecurringType.INTERVAL;
            tx.recurringValue = value;
            tx.recurringUnit = unit;
            return this;
        }

        public Builder nextDue(LocalDate nextDue) {
            tx.nextDue = nextDue;
            return this;
        }

        public RecurringBudgetTransaction build() {
            return tx;
        }
    }
}
