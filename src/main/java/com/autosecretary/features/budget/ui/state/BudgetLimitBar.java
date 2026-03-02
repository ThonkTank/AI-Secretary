package com.autosecretary.features.budget.ui.state;

/**
 * UI data model for a budget category spending progress bar.
 *
 * Represents one category's spending against its configured monthly limit,
 * with support for rollover carryover (unused budget from prior months may
 * increase the effective limit for the current month).
 *
 * percentage is computed as (spentCents / effectiveLimitCents * 100),
 * allowing the UI to render a visually accurate progress bar that may exceed 100%.
 *
 * @param categoryId         Unique ID of the budget category.
 * @param categoryName       Display name (may include emoji icon prefix).
 * @param categoryColorHex   Hex color code for this category (e.g., "#FF5733") for visual display.
 * @param spentCents         Total spent in this category during the current month (in cents).
 * @param baseLimitCents     The configured monthly spending limit for this category (in cents).
 * @param effectiveLimitCents Effective limit after applying rollover carryover from prior months;
 *                            may exceed baseLimitCents if carryover is available.
 */
public record BudgetLimitBar(String categoryId, String categoryName, String categoryColorHex,
                              long spentCents, long baseLimitCents, long effectiveLimitCents) {

    public int percentage() {
        return effectiveLimitCents > 0
                ? (int) ((double) spentCents / effectiveLimitCents * 100)
                : 0;
    }
}
