package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import com.autosecretary.features.budget.data.entity.BudgetAccount;
import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.domain.RecurringBudgetTransaction;
import com.autosecretary.features.budget.domain.TransactionDirection;

@Entity(
        tableName = "budget_recurring_template",
        foreignKeys = {
                @ForeignKey(
                        entity = BudgetAccount.class,
                        parentColumns = "id",
                        childColumns = "accountId",
                        onDelete = ForeignKey.RESTRICT,
                        onUpdate = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = BudgetCategory.class,
                        parentColumns = "id",
                        childColumns = "categoryId",
                        onDelete = ForeignKey.SET_NULL,
                        onUpdate = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("accountId"),
                @Index("categoryId")
        }
)
public class BudgetRecurringTemplateEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String accountId;

    @NonNull
    public String normalizedPayee;

    public String displayPayee;

    public String categoryId;

    public long avgAmountCents;

    public long minAmountCents;

    public long maxAmountCents;

    @NonNull
    public RecurringBudgetTransaction.RecurringType recurringType;

    @NonNull
    public TransactionDirection transactionType = TransactionDirection.EXPENSE;

    /**
     * Scheduling parameter whose meaning depends on recurringType:
     * - MONTHLY_DAY: day of month (1–31)
     * - INTERVAL: interval in days between occurrences
     * - WEEKLY: unused (0); day is stored in recurringDayOfWeek
     * - MONTHLY_LAST: unused (0)
     */
    @ColumnInfo(name = "recurringValue")
    public int schedulingParam;

    public DayOfWeek recurringDayOfWeek;

    public LocalDate nextDue;

    public boolean active = true;

    public BudgetRecurringTemplateEntity(@NonNull String accountId,
                                         @NonNull String normalizedPayee,
                                         @NonNull RecurringBudgetTransaction.RecurringType recurringType) {
        this.accountId = accountId;
        this.normalizedPayee = normalizedPayee;
        this.recurringType = recurringType;
    }

}
