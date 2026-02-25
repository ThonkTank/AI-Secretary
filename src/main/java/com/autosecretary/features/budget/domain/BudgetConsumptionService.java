package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransaction;

import java.util.List;

public class BudgetConsumptionService {

    public BudgetConsumption calculateMonthlyConsumption(BudgetLimit budgetLimit,
                                                         List<BudgetTransaction> transactions) {
        double spent = 0d;
        for (BudgetTransaction tx : transactions) {
            if (tx.categoryId == null || !"EXPENSE".equalsIgnoreCase(tx.type)) {
                continue;
            }
            if (!budgetLimit.categoryId.equals(tx.categoryId)) {
                continue;
            }
            if (!budgetLimit.yearMonth.equals(tx.yearMonth)) {
                continue;
            }
            spent += Math.abs(tx.amount);
        }

        double remaining = budgetLimit.amount - spent;
        double usage = budgetLimit.amount > 0d ? (spent / budgetLimit.amount) : 0d;
        return new BudgetConsumption(spent, remaining, usage, remaining < 0d);
    }

    public record BudgetConsumption(
            double spent,
            double remaining,
            double usageRatio,
            boolean overBudget
    ) {}
}
