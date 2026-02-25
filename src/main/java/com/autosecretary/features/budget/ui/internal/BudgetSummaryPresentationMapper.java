package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.projection.CategorySpendTotal;
import com.autosecretary.features.budget.data.projection.MonthlyTransactionOverviewItem;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;

import java.util.ArrayList;
import java.util.List;

public class BudgetSummaryPresentationMapper {

    public BudgetSummaryData toSummary(List<MonthlyTransactionOverviewItem> items, long freeBudgetCents) {
        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (MonthlyTransactionOverviewItem item : items) {
            if ("INTERNAL_TRANSFER".equals(item.transactionKind)) {
                continue;
            }
            if ("EXPENSE".equals(item.type)) {
                totalExpenseCents += item.amountCents;
            } else {
                totalIncomeCents += item.amountCents;
            }
        }

        return new BudgetSummaryData(totalIncomeCents, totalExpenseCents, freeBudgetCents);
    }

    public List<BudgetLimitBar> toLimitBars(List<CategorySpendTotal> totals,
                                             EffectiveLimitProvider effectiveLimitProvider,
                                             String yearMonth) {
        List<BudgetLimitBar> bars = new ArrayList<>();
        for (CategorySpendTotal total : totals) {
            if (total.limitAmount <= 0) {
                continue;
            }
            String icon = total.categoryIcon != null && !total.categoryIcon.trim().isEmpty()
                    ? total.categoryIcon : BudgetCategory.DEFAULT_ICON;
            String label = icon + " " + total.categoryName;
            Long effectiveLimitCents = effectiveLimitProvider.getEffectiveLimitCents(total.categoryId, yearMonth);
            double effectiveLimitEuros = effectiveLimitCents != null
                    ? (effectiveLimitCents / 100.0)
                    : total.limitAmount;

            bars.add(new BudgetLimitBar(
                    total.categoryId,
                    label,
                    total.categoryColorHex,
                    total.spentCents,
                    total.limitAmount,
                    effectiveLimitEuros));
        }
        return bars;
    }

    public interface EffectiveLimitProvider {
        Long getEffectiveLimitCents(String categoryId, String yearMonth);
    }
}
