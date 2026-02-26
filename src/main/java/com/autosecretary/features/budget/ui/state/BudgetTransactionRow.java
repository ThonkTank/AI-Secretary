package com.autosecretary.features.budget.ui.state;

import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.ui.internal.CurrencyFormatter;

import java.time.LocalDate;

public class BudgetTransactionRow {
    private final String transactionId;
    private final String label;
    private final TransactionDirection direction;
    private final String categoryColorHex;
    private final long amountCents;
    private final String categoryId;
    private final String note;
    private final LocalDate bookingDate;
    private final String accountId;

    public BudgetTransactionRow(String transactionId, String label,
                                TransactionDirection direction,
                                String categoryColorHex, long amountCents,
                                String categoryId, String note, LocalDate bookingDate, String accountId) {
        this.transactionId = transactionId;
        this.label = label;
        this.direction = direction;
        this.categoryColorHex = categoryColorHex;
        this.amountCents = amountCents;
        this.categoryId = categoryId;
        this.note = note;
        this.bookingDate = bookingDate;
        this.accountId = accountId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getLabel() {
        return label;
    }

    /** Returns a formatted Euro amount with direction sign, e.g. {@code "-3.50 €"} or {@code "+12.00 €"}. */
    public String getAmount() {
        return CurrencyFormatter.eurosWithSign(amountCents, direction);
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    /** Convenience method for UI boolean checks. */
    public boolean isExpense() {
        return direction == TransactionDirection.EXPENSE;
    }

    public String getCategoryColorHex() {
        return categoryColorHex;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public String getAccountId() {
        return accountId;
    }
}
