package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransaction;

import java.time.LocalDate;
import java.util.List;

public class BudgetConsumptionService {

    public BudgetConsumption calculateMonthlyConsumption(BudgetLimit budgetLimit, List<BudgetTransaction> transactions) {
        double spent = 0d;
        for (BudgetTransaction tx : transactions) {
            if ("INCOME".equals(tx.type) || tx.categoryId == null || tx.bookingDate == null) {
                continue;
            }
            if (!budgetLimit.categoryId.equals(tx.categoryId)) {
                continue;
            }
            String monthOfTransaction = toYearMonth(tx.bookingDate);
            if (!budgetLimit.yearMonth.equals(monthOfTransaction)) {
                continue;
            }
            spent += Math.abs(tx.amount);
        }

        double budgetBase = budgetLimit.amount;
        double remaining = budgetBase - spent;
        double usage = budgetBase > 0 ? (spent / budgetBase) : 0d;
        return new BudgetConsumption(spent, remaining, usage, remaining < 0);
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }

    public record BudgetConsumption(
            double spentAmount,
            double remainingAmount,
            double usageRatio,
            boolean overBudget
    ) {}
}
