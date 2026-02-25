package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.Account;
import com.autosecretary.features.budget.data.Transaction;

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
            if (tx.transactionDate == null || !YearMonthKey.from(tx.transactionDate).equals(yearMonth)) {
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


    public record Summary(
            int totalBalanceCents,
            int monthlyIncomeCents,
            int monthlyExpensesCents,
            int monthlyNetCents
    ) {}
}
