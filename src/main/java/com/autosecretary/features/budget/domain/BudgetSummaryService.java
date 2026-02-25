package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.Account;
import com.autosecretary.features.budget.data.Transaction;

import java.time.LocalDate;
import java.util.List;

public class BudgetSummaryService {

    public Summary calculateSummary(List<Account> accounts, List<Transaction> transactions, String yearMonth) {
        int totalBalance = 0;
        for (Account account : accounts) {
            if (account.includeInTotal) {
                totalBalance += account.currentBalanceCents;
            }
        }

        int monthlyIncome = 0;
        int monthlyExpenses = 0;
        for (Transaction tx : transactions) {
            LocalDate txDate = tx.transactionDate;
            if (txDate == null || !toYearMonth(txDate).equals(yearMonth)) {
                continue;
            }
            if (tx.isIncome) {
                monthlyIncome += tx.amountCents;
            } else {
                monthlyExpenses += Math.abs(tx.amountCents);
            }
        }

        return new Summary(totalBalance, monthlyIncome, monthlyExpenses, monthlyIncome - monthlyExpenses);
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }

    public record Summary(
            int totalBalanceCents,
            int monthlyIncomeCents,
            int monthlyExpensesCents,
            int monthlyNetCents
    ) {}
}
