package com.autosecretary.features.budget.data.legacy;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Transitional legacy transaction model kept for historical reference only.
 *
 * <p>Canonical budget persistence uses {@code BudgetTransactionEntity} in table
 * {@code budget_transaction} (introduced by AppDatabase migration v8). Do not use this class
 * for new code.</p>
 */
@Deprecated
@Entity(tableName = "transactions")
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    public Long id;
    public Long accountId;
    public int amountCents;
    public LocalDate transactionDate;
    public LocalDate valueDate;
    public Long categoryId;
    public String subcategory;
    public String description;
    public String payee;
    public boolean isIncome;
    public boolean isRecurring;
    public RecurringType repetitionType;
    public int repetitionValue;
    public RepUnits repetitionUnit;
    public DayOfWeek dayOfWeek;
    public LocalDate nextDue;
    public LocalDate lastOccurrence;
    public Integer amountMinCents;
    public Integer amountMaxCents;
    public Integer amountAvgCents;
    public int occurrenceCount;
    public Long parentRecurringId;
    public Long linkedTransactionId;
    public Long importId;
    public String importHash;
    public boolean isConfirmed;
    public boolean isPredicted;
    public LocalDateTime created;

    public enum RecurringType {
        MONTHLY_DAY,
        MONTHLY_LAST,
        WEEKLY,
        INTERVAL
    }

    public enum RepUnits {
        DAY,
        WEEK,
        MONTH
    }
}
