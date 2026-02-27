package com.autosecretary.features.budget.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;


import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;

import java.time.LocalDate;
import java.time.YearMonth;
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
                        entity = BudgetTransactionEntity.class,
                        parentColumns = "id",
                        childColumns = "linkedTransactionId",
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
                @Index("importHash"),
                @Index("linkedTransactionId")
        }
)
public class BudgetTransactionEntity {

    @PrimaryKey
    @NonNull
    public String id = UUID.randomUUID().toString();

    @NonNull
    public String accountId;

    public String categoryId;

    public String templateId;

    @NonNull
    @ColumnInfo(name = "type")
    public TransactionDirection direction = TransactionDirection.EXPENSE;

    @NonNull
    public TransactionKind transactionKind = TransactionKind.STANDARD;

    /**
     * References the paired transaction in a transfer relationship (when {@code transactionKind = INTERNAL_TRANSFER}).
     * Invariant: {@code linkedTransactionId} must never equal {@code id} (self-reference is forbidden).
     * This is enforced at the application level; the database FK does not prevent it.
     */
    public String linkedTransactionId;

    public long amountCents;

    @NonNull
    public LocalDate bookingDate;

    /**
     * Normalized value in format yyyy-MM for fast monthly queries.
     * Derived from bookingDate. Do not set directly — use setBookingDate() instead.
     */
    @NonNull
    public String yearMonth;

    public String note;

    public String importHash;

    public String payee;

    public String importId;

    public BudgetTransactionEntity(@NonNull String accountId, String categoryId,
                                   @NonNull TransactionDirection direction, long amountCents,
                                   @NonNull LocalDate bookingDate) {
        this.accountId = accountId;
        this.categoryId = categoryId;
        this.direction = direction;
        this.amountCents = amountCents;
        setBookingDate(bookingDate);
    }

    /** Sets bookingDate and derives yearMonth automatically. Use this instead of setting both fields directly. */
    public void setBookingDate(@NonNull LocalDate date) {
        this.bookingDate = date;
        this.yearMonth = YearMonth.from(date).toString();
    }
}
