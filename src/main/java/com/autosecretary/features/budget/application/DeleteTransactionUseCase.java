package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.BudgetTransaction;
import com.autosecretary.features.budget.domain.AccountBalanceRecalculationService;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

import java.time.LocalDate;

public class DeleteTransactionUseCase {
    private final BudgetRepository repository;
    private final AccountBalanceRecalculationService balanceService;
    private final BudgetConsumptionService consumptionService;

    public DeleteTransactionUseCase(BudgetRepository repository,
                                    AccountBalanceRecalculationService balanceService,
                                    BudgetConsumptionService consumptionService) {
        this.repository = repository;
        this.balanceService = balanceService;
        this.consumptionService = consumptionService;
    }

    public void execute(BudgetTransaction transaction) {
        if (transaction == null || transaction.id == null) {
            return;
        }

        repository.deleteTransaction(transaction.id);

        if (transaction.accountId != null) {
            balanceService.calculateNetAmount(repository.findTransactionsForAccount(transaction.accountId));
        }

        if (!"INCOME".equals(transaction.type) && transaction.categoryId != null && transaction.bookingDate != null) {
            String yearMonth = toYearMonth(transaction.bookingDate);
            BudgetLimit limit = repository.findBudgetLimit(transaction.categoryId, yearMonth);
            if (limit != null) {
                consumptionService.calculateMonthlyConsumption(limit, repository.findAllTransactions());
                repository.saveBudgetLimit(limit);
            }
        }
    }

    private String toYearMonth(LocalDate date) {
        return String.format("%d-%02d", date.getYear(), date.getMonthValue());
    }
}
