package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetTransaction;

import java.util.List;

public class BudgetSummaryService {

    public Summary calculateSummary(List<BudgetAccount> accounts,
                                    List<BudgetTransaction> transactions,
                                    String yearMonth) {
        double totalBalance = 0d;
        for (BudgetTransaction tx : transactions) {
            totalBalance += signedAmount(tx);
        }

        double monthlyIncome = 0d;
        double monthlyExpenses = 0d;
        for (BudgetTransaction tx : transactions) {
            if (!yearMonth.equals(tx.yearMonth)) {
                continue;
            }
            if ("INCOME".equalsIgnoreCase(tx.type)) {
                monthlyIncome += Math.abs(tx.amount);
            } else {
                monthlyExpenses += Math.abs(tx.amount);
            }
        }

        return new Summary(totalBalance, monthlyIncome, monthlyExpenses, monthlyIncome - monthlyExpenses,
                accounts.size());
    }

    private double signedAmount(BudgetTransaction transaction) {
        return "INCOME".equalsIgnoreCase(transaction.type)
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
