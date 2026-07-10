package com.autosecretary.features.budget.application;

import com.autosecretary.features.budget.application.overview.BudgetLimitOverviewItem;
import com.autosecretary.features.budget.application.overview.BudgetOverviewMapper;
import com.autosecretary.features.budget.domain.BudgetLimit;
import com.autosecretary.features.budget.domain.BudgetRepository;
import com.autosecretary.features.budget.domain.CategorySpendSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LoadBudgetLimitOverviewUseCase {
    private final BudgetRepository repository;
    private final CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase;

    public LoadBudgetLimitOverviewUseCase(BudgetRepository repository,
                                          CalculateEffectiveBudgetLimitUseCase calculateEffectiveLimitUseCase) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.calculateEffectiveLimitUseCase = Objects.requireNonNull(
                calculateEffectiveLimitUseCase, "calculateEffectiveLimitUseCase");
    }

    public List<BudgetLimitOverviewItem> execute(String yearMonth) {
        List<BudgetLimitOverviewItem> items = new ArrayList<>();
        for (CategorySpendSummary total : repository.getCategorySpendTotals(yearMonth)) {
            if (total.limitAmountCents() <= 0) {
                continue;
            }
            Long effectiveLimitCents = calculateEffectiveLimitUseCase
                    .execute(total.categoryId(), yearMonth)
                    .effectiveLimitCents();
            long effectiveCents = effectiveLimitCents != null
                    ? effectiveLimitCents
                    : total.limitAmountCents();
            items.add(new BudgetLimitOverviewItem(
                    total.categoryId(),
                    BudgetOverviewMapper.categoryLabel(total.categoryIcon(), total.categoryName()),
                    total.categoryColorHex(),
                    total.spentCents(),
                    total.limitAmountCents(),
                    effectiveCents));
        }
        return items;
    }

    public void saveBudgetLimit(BudgetLimit limit) {
        repository.saveBudgetLimit(limit);
    }
}
