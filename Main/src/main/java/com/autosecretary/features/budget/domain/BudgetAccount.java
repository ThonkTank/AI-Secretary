package com.autosecretary.features.budget.domain;

public record BudgetAccount(
        String id,
        String name,
        String currency,
        BudgetAccountType accountType,
        long currentBalanceCents,
        String accountNumber,
        boolean archived
) {
    public static BudgetAccount create(String name) {
        return new BudgetAccount(null, name, "EUR", BudgetAccountType.CHECKING, 0L, null, false);
    }
}
