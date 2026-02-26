package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
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

    public int avgAmountCents;

    public int minAmountCents;

    public int maxAmountCents;

    @NonNull
    public RecurringBudgetTransaction.RecurringType recurringType;

    public int recurringValue;

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
