package com.autosecretary.features.budget.domain;

/**
 * Discriminates standard transactions from linked transfer legs.
 *
 * <p>{@code INTERNAL_TRANSFER} transactions always exist as a <b>paired row</b>: a debit on the
 * source account and a matching credit on the target account. The two rows reference each other via
 * {@code linkedTransactionId}. Use {@link BudgetRepository#createTransfer} and
 * {@link BudgetRepository#updateTransfer} to create and update transfers so that both legs are
 * written atomically — never insert or modify one leg in isolation.
 */
public enum TransactionKind {
    STANDARD,
    INTERNAL_TRANSFER
}
