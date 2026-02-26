package com.autosecretary.features.budget.ui.state;

import java.time.LocalDate;

public class BudgetTransactionRow {
    private final String transactionId;
    private final String label;
    private final String amount;
    private final boolean isExpense;
    private final String categoryColorHex;
    private final long amountCents;
    private final String categoryId;
    private final String note;
    private final LocalDate bookingDate;
    private final String accountId;

    public BudgetTransactionRow(String transactionId, String label, String amount, boolean isExpense,
                                String categoryColorHex, long amountCents,
                                String categoryId, String note, LocalDate bookingDate, String accountId) {
        this.transactionId = transactionId;
        this.label = label;
        this.amount = amount;
        this.isExpense = isExpense;
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

    public String getAmount() {
        return amount;
    }

    public boolean isExpense() {
        return isExpense;
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
