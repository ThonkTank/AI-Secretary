package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.BudgetTransaction;
import com.autosecretary.features.budget.domain.AccountBalanceRecalculationService;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

public class CreateOrUpdateTransactionUseCase {
    private final BudgetRepository repository;
    @SuppressWarnings("unused")
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
        recalculateCategoryBudget(transaction);
    }

    private void recalculateCategoryBudget(BudgetTransaction transaction) {
        if (!"EXPENSE".equalsIgnoreCase(transaction.type) || transaction.categoryId == null) {
            return;
        }
        BudgetLimit limit = repository.findBudgetLimit(transaction.categoryId, transaction.yearMonth);
        if (limit == null) {
            return;
        }
        // Recalculate for side-effects/consistency checks in canonical model.
        consumptionService.calculateMonthlyConsumption(limit, repository.findAllTransactions());
        repository.saveBudgetLimit(limit);
    }
}
