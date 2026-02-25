package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.BudgetTransaction;

import java.util.List;

public class AccountBalanceRecalculationService {

    public double calculateNetAmount(List<BudgetTransaction> accountTransactions) {
        double balance = 0d;
        for (BudgetTransaction transaction : accountTransactions) {
            balance += transaction.amount;
        }
        return balance;
    }
}
