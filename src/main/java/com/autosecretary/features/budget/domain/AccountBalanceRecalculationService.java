package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.Account;
import com.autosecretary.features.budget.data.Transaction;

import java.util.List;

public class AccountBalanceRecalculationService {

    public int recalculateBalance(Account account, List<Transaction> accountTransactions) {
        int balance = account.initialBalanceCents;
        for (Transaction transaction : accountTransactions) {
            balance += transaction.amountCents;
        }
        account.currentBalanceCents = balance;
        return balance;
    }
}
