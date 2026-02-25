package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetTransaction;

import java.util.List;

/**
 * Computes a running account balance from canonical transactions.
 *
 * <p>The v8 canonical schema does not persist mutable balance fields on accounts. This service
 * therefore returns a derived value instead of mutating account entities.</p>
 */
public class AccountBalanceRecalculationService {

    public double recalculateBalance(List<BudgetTransaction> accountTransactions) {
        double balance = 0d;
        for (BudgetTransaction transaction : accountTransactions) {
            balance += signedAmount(transaction);
        }
        return balance;
    }

    private double signedAmount(BudgetTransaction transaction) {
        return "INCOME".equalsIgnoreCase(transaction.type)
                ? Math.abs(transaction.amount)
                : -Math.abs(transaction.amount);
    }
}
