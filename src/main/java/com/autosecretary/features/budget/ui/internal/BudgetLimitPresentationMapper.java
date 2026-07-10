package com.autosecretary.features.budget.ui.internal;

import com.autosecretary.features.budget.application.overview.BudgetLimitOverviewItem;
import com.autosecretary.features.budget.ui.state.BudgetLimitBar;

import java.util.ArrayList;
import java.util.List;

public final class BudgetLimitPresentationMapper {
    private BudgetLimitPresentationMapper() {
    }

    public static List<BudgetLimitBar> toLimitBars(List<BudgetLimitOverviewItem> items) {
        List<BudgetLimitBar> bars = new ArrayList<>();
        for (BudgetLimitOverviewItem item : items) {
            bars.add(new BudgetLimitBar(
                    item.categoryId(),
                    item.categoryName(),
                    item.categoryColorHex(),
                    item.spentCents(),
                    item.baseLimitCents(),
                    item.effectiveLimitCents()));
        }
        return bars;
    }
}
