package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.Transaction;

import java.util.List;

public class BudgetConsumptionService {

    public BudgetConsumption calculateMonthlyConsumption(BudgetLimit budgetLimit, List<Transaction> transactions) {
        int spent = 0;
        for (Transaction tx : transactions) {
            if (tx.isIncome || tx.categoryId == null || tx.transactionDate == null) {
                continue;
            }
            if (!budgetLimit.categoryId.equals(tx.categoryId)) {
                continue;
            }
            String monthOfTransaction = YearMonthKey.from(tx.transactionDate);
            if (!budgetLimit.yearMonth.equals(monthOfTransaction)) {
                continue;
            }
            spent += Math.abs(tx.amountCents);
        }

        int budgetBase = budgetLimit.limitCents + budgetLimit.rolloverCents;
        int remaining = budgetBase - spent;
        double usage = budgetBase > 0 ? ((double) spent / budgetBase) : 0d;
        return new BudgetConsumption(spent, remaining, usage, remaining < 0);
    }


    public record BudgetConsumption(
            int spentCents,
            int remainingCents,
            double usageRatio,
            boolean overBudget
    ) {}
}
