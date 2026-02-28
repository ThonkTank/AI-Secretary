package com.autosecretary.features.budget.ui.state;

import com.autosecretary.features.budget.domain.TransactionDirection;

import java.time.LocalDate;

/**
 * Immutable UI read model for a single transaction in a transaction list.
 *
 * Flattened representation of a domain BudgetTransactionEntity with all display-ready fields.
 * Built via the builder pattern to support flexible mapping from domain models.
 * Use this class exclusively in UI and ViewModel layers; domain layer uses BudgetTransactionEntity.
 *
 * All monetary amounts are in cents (divide by 100 for display in currency units).
 * categoryColorHex is computed by the mapping layer based on the category's configured color.
 */
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

    private BudgetTransactionRow(Builder b) {
        this.transactionId = b.transactionId;
        this.label = b.label;
        this.direction = b.direction;
        this.categoryColorHex = b.categoryColorHex;
        this.amountCents = b.amountCents;
        this.categoryId = b.categoryId;
        this.note = b.note;
        this.bookingDate = b.bookingDate;
        this.accountId = b.accountId;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for constructing immutable BudgetTransactionRow instances.
     * Required fields: transactionId, label, direction, bookingDate, accountId.
     * Optional fields: categoryColorHex, categoryId, note (may be null).
     */
    public static class Builder {
        private String transactionId;
        private String label;
        private TransactionDirection direction;
        private String categoryColorHex;
        private long amountCents;
        private String categoryId;
        private String note;
        private LocalDate bookingDate;
        private String accountId;

        public Builder transactionId(String v)       { transactionId = v; return this; }
        public Builder label(String v)               { label = v; return this; }
        public Builder direction(TransactionDirection v) { direction = v; return this; }
        public Builder categoryColorHex(String v)    { categoryColorHex = v; return this; }
        public Builder amountCents(long v)           { amountCents = v; return this; }
        public Builder categoryId(String v)          { categoryId = v; return this; }
        public Builder note(String v)                { note = v; return this; }
        public Builder bookingDate(LocalDate v)      { bookingDate = v; return this; }
        public Builder accountId(String v)           { accountId = v; return this; }

        public BudgetTransactionRow build() {
            validateState();
            return new BudgetTransactionRow(this);
        }

        private void validateState() {
            if (transactionId == null) throw new IllegalStateException("transactionId must be set");
            if (label == null) throw new IllegalStateException("label must be set");
            if (direction == null) throw new IllegalStateException("direction must be set");
            if (bookingDate == null) throw new IllegalStateException("bookingDate must be set");
            if (accountId == null) throw new IllegalStateException("accountId must be set");
            // categoryId, categoryColorHex, note are optional — uncategorized transactions and
            // notes-free transactions pass null; callers must handle null defensively.
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getLabel() {
        return label;
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
