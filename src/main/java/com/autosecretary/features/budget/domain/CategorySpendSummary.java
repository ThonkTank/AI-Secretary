package com.autosecretary.features.budget.domain;

/**
 * Read projection: how much was spent in a category for a given month, and what the budget limit is.
 * <p>
 * Produced by {@link BudgetRepository#getCategorySpendTotals(String)} via a database aggregate query.
 * Consumed by the budget overview UI to render per-category spend bars.
 * <p>
 * Note on {@code limitAmountCents}: a value of {@code 0} means <em>no limit has been set</em> for
 * this category (not a limit of zero). The UI should treat 0 as "unlimited" and hide the limit bar.
 */
public record CategorySpendSummary(
        /** Unique category identifier (UUID). */
        String categoryId,
        /** Human-readable category name (e.g. "Groceries"). */
        String categoryName,
        /** Icon identifier for UI display (e.g. Material icon name or emoji). */
        String categoryIcon,
        /** Hex color string for the category chip/badge (e.g. "#FF5722"). */
        String categoryColorHex,
        /** Total amount spent in this category for the queried month, in cents. Always non-negative. */
        long spentCents,
        /** Monthly budget limit for this category, in cents. {@code 0} means no limit is set. */
        long limitAmountCents
) {}
