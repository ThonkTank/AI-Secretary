package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.BudgetTransaction;
import com.autosecretary.features.budget.domain.AccountBalanceRecalculationService;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

import java.time.LocalDate;

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

    public void execute(BudgetTransaction transaction) {
        repository.saveTransaction(transaction);
        recalculateAccountBalance(transaction.accountId);
        recalculateCategoryBudget(transaction);
    }

    private void recalculateAccountBalance(String accountId) {
        if (accountId == null) {
            return;
        }
        balanceService.calculateNetAmount(repository.findTransactionsForAccount(accountId));
    }

    private void recalculateCategoryBudget(BudgetTransaction transaction) {
        if ("INCOME".equals(transaction.type) || transaction.categoryId == null || transaction.bookingDate == null) {
            return;
        }
        String yearMonth = toYearMonth(transaction.bookingDate);
        BudgetLimit limit = repository.findBudgetLimit(transaction.categoryId, yearMonth);
        if (limit == null) {
            return;
        }
        consumptionService.calculateMonthlyConsumption(limit, repository.findAllTransactions());
        repository.saveBudgetLimit(limit);
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }
}
