package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetAccount;
import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.BudgetRepository;

import java.util.List;

public class BudgetTransactionService {
    private final BudgetRepository repository;

    public BudgetTransactionService(BudgetRepository repository) {
        this.repository = repository;
    }

    public void saveTransaction(BudgetTransactionEntity transaction) {
        repository.saveTransaction(transaction);
    }

    public void deleteTransaction(BudgetTransactionEntity transaction) {
        if (transaction == null || transaction.id == null) {
            return;
        }
        repository.deleteTransaction(transaction.id);
    }

    public void setBudgetLimit(BudgetLimit limit) {
        if (limit == null || limit.categoryId == null || limit.yearMonth == null) {
            return;
        }
        BudgetLimit existingLimit = repository.findBudgetLimit(limit.categoryId, limit.yearMonth);
        if (existingLimit != null) {
            limit.id = existingLimit.id;
        }
        repository.saveBudgetLimit(limit);
    }

    public BudgetOverview loadOverview(String yearMonth) {
        List<BudgetAccount> accounts = repository.findActiveAccounts();
        List<BudgetTransactionEntity> transactions = repository.findAllTransactions();
        Summary summary = calculateSummary(transactions, yearMonth);
        return new BudgetOverview(summary, accounts, transactions);
    }

    private Summary calculateSummary(List<BudgetTransactionEntity> transactions, String yearMonth) {
        double totalBalance = 0d;
        for (BudgetTransactionEntity tx : transactions) {
            totalBalance += tx.type == BudgetTransactionEntity.TransactionType.INCOME
                    ? Math.abs(tx.amount) : -Math.abs(tx.amount);
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

        return new Summary(totalBalance, monthlyIncome, monthlyExpenses, monthlyIncome - monthlyExpenses);
    }

    public record BudgetOverview(
            Summary summary,
            List<BudgetAccount> accounts,
            List<BudgetTransactionEntity> transactions
    ) {}

    public record Summary(
            double totalBalance,
            double monthlyIncome,
            double monthlyExpenses,
            double monthlyNet
    ) {}
}
