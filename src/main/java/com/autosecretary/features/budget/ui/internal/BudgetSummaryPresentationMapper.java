package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.data.entity.BudgetCategory;
import com.autosecretary.features.budget.data.entity.BudgetTransactionEntity;
import com.autosecretary.features.budget.domain.CategorySpendSummary;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.domain.TransactionDirection;
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
            if (item.transactionKind == BudgetTransactionEntity.TransactionKind.INTERNAL_TRANSFER) {
                continue;
            }
            if (item.direction == TransactionDirection.EXPENSE) {
                totalExpenseCents += item.amountCents;
            } else {
                totalIncomeCents += item.amountCents;
            }
        }

        return new BudgetSummaryData(totalIncomeCents, totalExpenseCents, freeBudgetCents);
    }

    /**
     * Builds a display label for a category, e.g. {@code "🍕 Lebensmittel"}.
     * Falls back to {@link BudgetCategory#DEFAULT_ICON} when the icon field is blank.
     */
    public static String categoryLabel(String icon, String name) {
        String resolvedIcon = icon != null && !icon.trim().isEmpty()
                ? icon : BudgetCategory.DEFAULT_ICON;
        return resolvedIcon + " " + name;
    }

    public List<BudgetLimitBar> toLimitBars(List<CategorySpendSummary> totals,
                                             BiFunction<String, String, Long> effectiveLimitProvider,
                                             String yearMonth) {
        List<BudgetLimitBar> bars = new ArrayList<>();
        for (CategorySpendSummary total : totals) {
            if (total.limitAmountCents() <= 0) {
                continue;
            }
            String label = categoryLabel(total.categoryIcon(), total.categoryName());
            Long effectiveLimitCents = effectiveLimitProvider.apply(total.categoryId(), yearMonth);
            long resolvedEffectiveLimitCents = effectiveLimitCents != null
                    ? effectiveLimitCents
                    : total.limitAmountCents();

            bars.add(new BudgetLimitBar(
                    total.categoryId(),
                    label,
                    total.categoryColorHex(),
                    total.spentCents(),
                    total.limitAmountCents(),
                    resolvedEffectiveLimitCents));
        }
        return bars;
    }

}
