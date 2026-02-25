package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;

import java.util.List;

public class BudgetSummaryService {

    public Summary calculateSummary(List<BudgetAccount> accounts,
                                    List<BudgetTransactionEntity> transactions,
                                    String yearMonth) {
        double totalBalance = 0d;
        for (BudgetTransactionEntity tx : transactions) {
            totalBalance += signedAmount(tx);
        }

        double monthlyIncome = 0d;
        double monthlyExpenses = 0d;
        for (BudgetTransactionEntity tx : transactions) {
            if (!yearMonth.equals(tx.yearMonth)) {
                continue;
            }
            if (tx.type == BudgetTransactionEntity.TransactionType.INCOME) {
                monthlyIncome += Math.abs(tx.amount);
            } else {
                monthlyExpenses += Math.abs(tx.amount);
            }
        }

        return new Summary(totalBalance, monthlyIncome, monthlyExpenses, monthlyIncome - monthlyExpenses,
                accounts.size());
    }

    private double signedAmount(BudgetTransactionEntity transaction) {
        return transaction.type == BudgetTransactionEntity.TransactionType.INCOME
                ? Math.abs(transaction.amount)
                : -Math.abs(transaction.amount);
    }

    public record Summary(
            double totalBalance,
            double monthlyIncome,
            double monthlyExpenses,
            double monthlyNet,
            int accountCount
    ) {}
}
