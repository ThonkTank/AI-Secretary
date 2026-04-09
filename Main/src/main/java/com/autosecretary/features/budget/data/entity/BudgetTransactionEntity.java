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

/**
 * Represents a single budget transaction (income or expense).
 *
 * Transactions are always associated with an account and may be linked to:
 * - A category (for classification/reporting)
 * - A recurring template (if auto-generated from a recurring pattern)
 * - Another transaction (if part of an internal transfer pair)
 *
 * The {@code bookingDate} and {@code yearMonth} fields must stay in sync.
 * Always use {@link #setBookingDate(LocalDate)} to update dates; never set
 * them independently. The yearMonth field is denormalized for fast month-based
 * queries (e.g., "sum all transactions in a month").
 */
@Entity(
        tableName = "budget_transaction",
        foreignKeys = {
                @ForeignKey(
                        entity = BudgetAccountEntity.class,
                        parentColumns = "id",
                        childColumns = "accountId",
                        onDelete = ForeignKey.RESTRICT,
                        onUpdate = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = BudgetCategoryEntity.class,
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
                @Index("linkedTransactionId"),
                // Composite index for timeline queries: getDailyDeltasForAccount,
                // getNetAmountBeforeDateForAccount, and findByAccountId all filter on
                // (accountId, bookingDate). Without this index SQLite scans every row for
                // the account before applying the date predicate; with it the range scan
                // touches only the matching rows directly.
                @Index(value = {"accountId", "bookingDate"})
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

    // Column is named "type" in the database for historical reasons; kept as-is to avoid
    // a destructive schema migration. Java field name uses the clearer "direction".
    // In SQL queries, always reference this column as "type" (not "direction").
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

    /**
     * Transaction amount in cents, always stored as a positive value.
     * The sign (income vs. expense) is conveyed by the {@link #direction} field, not by negating this value.
     * Sign is only applied when computing balances or deltas (e.g., INCOME adds +amountCents, EXPENSE subtracts -amountCents).
     */
    public long amountCents;

    @NonNull
    public LocalDate bookingDate;

    /**
     * Normalized year-month in format yyyy-MM, derived from bookingDate.
     * This is a denormalized field for query performance: filtering and grouping by month
     * is much faster with a pre-computed string index than deriving it from bookingDate each time.
     * <strong>Invariant:</strong> Must always match {@code YearMonth.from(bookingDate).toString()}.
     * <strong>Never set directly</strong> — use {@link #setBookingDate(LocalDate)} to maintain sync.
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

    /**
     * Sets bookingDate and derives yearMonth automatically, maintaining the invariant
     * that the two fields always stay in sync. Always use this method instead of setting
     * either field directly; breaking the sync will cause incorrect monthly aggregations.
     */
    public void setBookingDate(@NonNull LocalDate date) {
        this.bookingDate = date;
        this.yearMonth = YearMonth.from(date).toString();
    }
}
