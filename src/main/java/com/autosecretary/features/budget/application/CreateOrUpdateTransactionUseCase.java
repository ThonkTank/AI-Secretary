package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
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

    public void execute(BudgetTransactionEntity transaction) {
        repository.saveTransaction(transaction);
        recalculateCategoryBudget(transaction);
    }

    private void recalculateCategoryBudget(BudgetTransactionEntity transaction) {
        if (transaction.type != BudgetTransactionEntity.TransactionType.EXPENSE || transaction.categoryId == null) {
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
