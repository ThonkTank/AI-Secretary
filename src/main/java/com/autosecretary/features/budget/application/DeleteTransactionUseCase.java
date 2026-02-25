package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.data.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.AccountBalanceRecalculationService;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

public class DeleteTransactionUseCase {
    private final BudgetRepository repository;
    @SuppressWarnings("unused")
    private final AccountBalanceRecalculationService balanceService;
    private final BudgetConsumptionService consumptionService;

    public DeleteTransactionUseCase(BudgetRepository repository,
                                    AccountBalanceRecalculationService balanceService,
                                    BudgetConsumptionService consumptionService) {
        this.repository = repository;
        this.balanceService = balanceService;
        this.consumptionService = consumptionService;
    }

    public void execute(BudgetTransactionEntity transaction) {
        if (transaction == null || transaction.id == null) {
            return;
        }

        repository.deleteTransaction(transaction.id);

        if (transaction.type == BudgetTransactionEntity.TransactionType.EXPENSE && transaction.categoryId != null) {
            BudgetLimit limit = repository.findBudgetLimit(transaction.categoryId, transaction.yearMonth);
            if (limit != null) {
                // Recalculate for side-effects/consistency checks in canonical model.
                consumptionService.calculateMonthlyConsumption(limit, repository.findAllTransactions());
                repository.saveBudgetLimit(limit);
            }
        }
    }
}
