package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.Transaction;
import com.autosecretary.features.budget.domain.AccountBalanceRecalculationService;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

import java.time.LocalDate;
import java.util.List;

public class CreateOrUpdateTransactionUseCase {
    private final BudgetRepository repository;
    private final AccountBalanceRecalculationService balanceService;
    private final BudgetConsumptionService consumptionService;

    public CreateOrUpdateTransactionUseCase(BudgetRepository repository,
                                            AccountBalanceRecalculationService balanceService,
                                            BudgetConsumptionService consumptionService) {
        this.repository = repository;
        this.balanceService = balanceService;
        this.consumptionService = consumptionService;
    }

    public void execute(Transaction transaction) {
        repository.saveTransaction(transaction);
        recalculateAccountBalance(transaction.accountId);
        recalculateCategoryBudget(transaction);
    }

    private void recalculateAccountBalance(Long accountId) {
        if (accountId == null) {
            return;
        }
        var account = repository.findAccountById(accountId);
        if (account == null) {
            return;
        }
        balanceService.recalculateBalance(account, repository.findTransactionsForAccount(accountId));
        repository.saveAccount(account);
    }

    private void recalculateCategoryBudget(Transaction transaction) {
        if (transaction.isIncome || transaction.categoryId == null || transaction.transactionDate == null) {
            return;
        }
        String yearMonth = toYearMonth(transaction.transactionDate);
        BudgetLimit limit = repository.findBudgetLimit(transaction.categoryId, yearMonth);
        if (limit == null) {
            return;
        }
        List<Transaction> allTransactions = repository.findAllTransactions();
        limit.spentCents = consumptionService.calculateMonthlyConsumption(limit, allTransactions).spentCents();
        repository.saveBudgetLimit(limit);
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }
}
