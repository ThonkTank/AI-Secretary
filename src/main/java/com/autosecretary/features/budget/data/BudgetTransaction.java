package com.autosecretary.features.budget.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.time.LocalDate;
import java.util.UUID;

@Entity(
        tableName = "budget_transaction",
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
                @Index("categoryId"),
                @Index("yearMonth"),
                @Index("bookingDate")
        }
)
public class BudgetTransaction {
    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String accountId;

    public String categoryId;

    /**
     * Allowed values: INCOME, EXPENSE.
     */
    @NonNull
    public String type = "EXPENSE";

    public double amount;

    @NonNull
    public LocalDate bookingDate;

    /**
     * Normalized value in format yyyy-MM for fast monthly queries.
     */
    @NonNull
    public String yearMonth;

    public String note;

    public BudgetTransaction(@NonNull String accountId, String categoryId, @NonNull String type,
                             double amount, @NonNull LocalDate bookingDate, @NonNull String yearMonth) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.amount = amount;
        this.bookingDate = bookingDate;
        this.yearMonth = yearMonth;
    }
}
