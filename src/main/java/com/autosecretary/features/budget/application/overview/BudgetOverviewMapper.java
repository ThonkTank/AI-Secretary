package com.autosecretary.features.budget.application.overview;

import com.autosecretary.features.budget.domain.BudgetCategory;
import com.autosecretary.features.budget.domain.MonthlyOverviewItem;
import com.autosecretary.features.budget.domain.TransactionDirection;
import com.autosecretary.features.budget.domain.TransactionKind;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps domain/data objects to presentation-layer state objects for the budget overview.
 *
 * <p>Concerns handled here:
 * <ul>
 *   <li>{@link #toSummary} — aggregates monthly income/expense totals (skipping internal
 *       transfers) and wraps them with the account's running balance into {@link BudgetSummaryData}.
 *   <li>{@link #toLimitBars} — converts per-category spend totals into {@link BudgetLimitBar}
 *       display objects, applying rollover-adjusted effective limits where available.
 *   <li>{@link #categoryLabel} — utility for rendering a category's emoji icon + name as one
 *       display string; used across multiple dialog controllers and the overview loader.
 * </ul>
 */
public class BudgetOverviewMapper {

    private BudgetOverviewMapper() {}

    /** Display fallback icon when a category has no configured icon. */
    public static final String DEFAULT_CATEGORY_ICON = "🏷️";

    public static BudgetSummaryData toSummary(List<MonthlyOverviewItem> items, long freeBudgetCents) {
        long totalIncomeCents = 0;
        long totalExpenseCents = 0;

        for (MonthlyOverviewItem item : items) {
            // Internal transfers move money between accounts — they are neither income nor expense
            // and must be excluded to avoid distorting the summary totals.
            if (item.transactionKind() == TransactionKind.INTERNAL_TRANSFER) {
                continue;
            }
            if (item.direction() == TransactionDirection.EXPENSE) {
                totalExpenseCents += item.amountCents();
            } else {
                totalIncomeCents += item.amountCents();
            }
        }

        return new BudgetSummaryData(totalIncomeCents, totalExpenseCents, freeBudgetCents);
    }

    /**
     * Returns the subset of {@code allCategories} whose direction matches {@code direction}.
     * Used by dialog controllers to populate direction-specific category spinners.
     */
    public static List<BudgetCategory> categoriesForDirection(
            List<BudgetCategory> allCategories, TransactionDirection direction) {
        return allCategories.stream()
                .filter(c -> c.direction() == direction)
                .collect(Collectors.toList());
    }

    /**
     * Builds a display label for a category, e.g. {@code "🍕 Lebensmittel"}.
     * Falls back to {@link #DEFAULT_CATEGORY_ICON} when the icon field is blank.
     */
    public static String categoryLabel(String icon, String name) {
        String resolvedIcon = icon != null && !icon.isBlank()
                ? icon : DEFAULT_CATEGORY_ICON;
        return resolvedIcon + " " + name;
    }

}
