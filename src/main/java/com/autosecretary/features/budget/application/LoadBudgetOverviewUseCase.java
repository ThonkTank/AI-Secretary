package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.Account;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.Transaction;
import com.autosecretary.features.budget.domain.BudgetSummaryService;

import java.util.List;

public class LoadBudgetOverviewUseCase {
    private final BudgetRepository repository;
    private final BudgetSummaryService summaryService;

    public LoadBudgetOverviewUseCase(BudgetRepository repository,
                                     BudgetSummaryService summaryService) {
        this.repository = repository;
        this.summaryService = summaryService;
    }

    public BudgetOverview execute(String yearMonth) {
        List<Account> accounts = repository.findActiveAccounts();
        List<Transaction> transactions = repository.findAllTransactions();
        BudgetSummaryService.Summary summary = summaryService.calculateSummary(accounts, transactions, yearMonth);
        return new BudgetOverview(summary, accounts, transactions);
    }

    public record BudgetOverview(
            BudgetSummaryService.Summary summary,
            List<Account> accounts,
            List<Transaction> transactions
    ) {}
}
