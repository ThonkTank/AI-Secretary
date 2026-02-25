package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetTransactionEntity;

import java.util.List;

/**
 * Computes a running account balance from canonical transactions.
 *
 * <p>The v8 canonical schema does not persist mutable balance fields on accounts. This service
 * therefore returns a derived value instead of mutating account entities.</p>
 */
public class AccountBalanceRecalculationService {

    public double recalculateBalance(List<BudgetTransactionEntity> accountTransactions) {
        double balance = 0d;
        for (BudgetTransactionEntity transaction : accountTransactions) {
            balance += signedAmount(transaction);
        }
        return balance;
    }

    private double signedAmount(BudgetTransactionEntity transaction) {
        return transaction.type == BudgetTransactionEntity.TransactionType.INCOME
                ? Math.abs(transaction.amount)
                : -Math.abs(transaction.amount);
    }
}
