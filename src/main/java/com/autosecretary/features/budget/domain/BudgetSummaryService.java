package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetTransaction;

import java.time.LocalDate;
import java.util.List;

public class BudgetSummaryService {

    public Summary calculateSummary(List<BudgetAccount> accounts, List<BudgetTransaction> transactions, String yearMonth) {
        int activeAccounts = accounts.size();

        double monthlyIncome = 0;
        double monthlyExpenses = 0;
        for (BudgetTransaction tx : transactions) {
            LocalDate txDate = tx.bookingDate;
            if (txDate == null || !toYearMonth(txDate).equals(yearMonth)) {
                continue;
            }
            if ("INCOME".equals(tx.type)) {
                monthlyIncome += tx.amount;
            } else {
                monthlyExpenses += Math.abs(tx.amount);
            }
        }

        return new Summary(activeAccounts, monthlyIncome, monthlyExpenses, monthlyIncome - monthlyExpenses);
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }

    public record Summary(
            int activeAccounts,
            double monthlyIncome,
            double monthlyExpenses,
            double monthlyNet
    ) {}
}
