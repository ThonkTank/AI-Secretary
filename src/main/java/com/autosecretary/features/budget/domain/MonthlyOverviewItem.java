package com.autosecretary.features.budget.domain;

import java.time.LocalDate;

/**
 * Flat read projection of a single budget transaction for the monthly overview list.
 * <p>
 * Produced by {@link BudgetRepository#getMonthlyOverview(String)} and
 * {@link BudgetRepository#getMonthlyOverviewForAccount(String, String)} via Room {@code @Query}
 * with a JOIN across transactions, accounts, and categories. The public mutable fields are filled
 * by Room's column-to-field mapping.
 * <p>
 * This is a <em>read-only projection</em> — do not use it for writes. To create or update
 * transactions, use the corresponding {@link BudgetRepository} write methods.
 * <p>
 * For transfer transactions ({@code transactionKind == INTERNAL_TRANSFER}), {@code linkedTransactionId}
 * points to the matching leg (the counterpart account's transaction row).
 */
public class MonthlyOverviewItem {
    /** UUID of the transaction (primary key in the transaction table). */
    public String transactionId;
    /** Date the transaction was booked (from the statement or manual entry). */
    public LocalDate bookingDate;
    /** Month string in "YYYY-MM" format (used as the partition key for monthly queries). */
    public String yearMonth;
    /** Whether this is an income or expense transaction. */
    public TransactionDirection direction;
    /** {@code STANDARD} for regular transactions; {@code INTERNAL_TRANSFER} for transfer legs. */
    public TransactionKind transactionKind;
    /** Transaction amount in cents (absolute value; use {@code direction} for sign). */
    public long amountCents;
    /** Optional memo or description text. */
    public String note;
    /** UUID of the account this transaction belongs to. */
    public String accountId;
    /** Display name of the account (joined from the accounts table). */
    public String accountName;
    /** UUID of the assigned category, or null if uncategorized. */
    public String categoryId;
    /** Display name of the category (joined from the categories table). */
    public String categoryName;
    /** Icon identifier for the category (e.g. emoji or Material icon name). */
    public String categoryIcon;
    /** Hex color string for the category (e.g. "#FF5722"). */
    public String categoryColorHex;
    /**
     * For {@code INTERNAL_TRANSFER} transactions: the UUID of the paired transaction on the other
     * account. Null for standard transactions and for transfers missing their counterpart.
     */
    public String linkedTransactionId;
}
