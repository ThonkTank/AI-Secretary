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
                ),
                @ForeignKey(
                        entity = BudgetRecurringTemplateEntity.class,
                        parentColumns = "id",
                        childColumns = "templateId",
                        onDelete = ForeignKey.SET_NULL,
                        onUpdate = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index("accountId"),
                @Index("categoryId"),
                @Index("templateId"),
                @Index("yearMonth"),
                @Index("bookingDate"),
                @Index("importHash")
        }
)
public class BudgetTransactionEntity {
    public enum TransactionType {
        INCOME,
        EXPENSE
    }

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String accountId;

    public String categoryId;

    public String templateId;

    @NonNull
    public TransactionType type = TransactionType.EXPENSE;

    public long amountCents;

    @NonNull
    public LocalDate bookingDate;

    /**
     * Normalized value in format yyyy-MM for fast monthly queries.
     */
    @NonNull
    public String yearMonth;

    public String note;

    public String importHash;

    public String payee;

    public String importId;

    public BudgetTransactionEntity(@NonNull String accountId, String categoryId,
                                   @NonNull TransactionType type, long amountCents,
                                   @NonNull LocalDate bookingDate, @NonNull String yearMonth) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.type = type;
        this.amountCents = amountCents;
        this.bookingDate = bookingDate;
        this.yearMonth = yearMonth;
    }
}
