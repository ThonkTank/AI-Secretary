package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.data.BudgetLimit;
import com.autosecretary.features.budget.data.BudgetRepository;
import com.autosecretary.features.budget.domain.BudgetConsumptionService;

public class SetBudgetLimitUseCase {
    private final BudgetRepository repository;
    private final BudgetConsumptionService consumptionService;

    public SetBudgetLimitUseCase(BudgetRepository repository,
                                 BudgetConsumptionService consumptionService) {
        this.repository = repository;
        this.consumptionService = consumptionService;
    }

    public void execute(BudgetLimit limit) {
        if (limit == null || limit.categoryId == null || limit.yearMonth == null) {
            return;
        }

        BudgetLimit existingLimit = repository.findBudgetLimit(limit.categoryId, limit.yearMonth);
        if (existingLimit != null) {
            limit.id = existingLimit.id;
        }

        consumptionService.calculateMonthlyConsumption(limit, repository.findAllTransactions());
        repository.saveBudgetLimit(limit);
    }
}
