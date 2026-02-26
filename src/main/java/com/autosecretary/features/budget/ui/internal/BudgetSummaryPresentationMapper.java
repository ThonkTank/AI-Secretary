package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;
import com.autosecretary.features.budget.ui.state.BudgetSummaryData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class BudgetSummaryPresentationMapper {

    public BudgetSummaryData toSummary(List<MonthlyOverviewItem> items, long freeBudgetCents) {
        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (MonthlyOverviewItem item : items) {
            // Internal transfers move money between accounts — they are neither income nor expense
            // and must be excluded to avoid distorting the summary totals.
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

    public List<BudgetLimitBar> toLimitBars(List<CategorySpendSummary> totals,
                                             BiFunction<String, String, Long> effectiveLimitProvider,
                                             String yearMonth) {
        List<BudgetLimitBar> bars = new ArrayList<>();
        for (CategorySpendSummary total : totals) {
            if (total.limitAmountCents <= 0) {
                continue;
            }
            String icon = total.categoryIcon != null && !total.categoryIcon.trim().isEmpty()
                    ? total.categoryIcon : BudgetCategory.DEFAULT_ICON;
            String label = icon + " " + total.categoryName;
            Long effectiveLimitCents = effectiveLimitProvider.apply(total.categoryId, yearMonth);
            double effectiveLimitEuros = effectiveLimitCents != null
                    ? (effectiveLimitCents / 100.0)
                    : (total.limitAmountCents / 100.0);

            bars.add(new BudgetLimitBar(
                    total.categoryId,
                    label,
                    total.categoryColorHex,
                    total.spentCents,
                    total.limitAmountCents / 100.0,
                    effectiveLimitEuros));
        }
        return bars;
    }

}
