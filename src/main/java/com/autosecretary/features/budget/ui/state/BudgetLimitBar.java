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
 */
public class BudgetLimitBar {
    private final String categoryId;
    private final String categoryName;
    /** Hex color code for this category (e.g., "#FF5733") for visual display. */
    private final String categoryColorHex;
    /** Total spent in this category during the current month (in cents). */
    private final long spentCents;
    /** The configured monthly spending limit for this category (in cents). */
    private final long baseLimitCents;
    /** Effective limit after applying rollover carryover from prior months; may exceed baseLimitCents if carryover is available. */
    private final long effectiveLimitCents;
    private final int percentage;

    public BudgetLimitBar(String categoryId, String categoryName, String categoryColorHex,
                          long spentCents, long baseLimitCents, long effectiveLimitCents) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryColorHex = categoryColorHex;
        this.spentCents = spentCents;
        this.baseLimitCents = baseLimitCents;
        this.effectiveLimitCents = effectiveLimitCents;
        this.percentage = effectiveLimitCents > 0
                ? (int) ((double) spentCents / effectiveLimitCents * 100)
                : 0;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getCategoryColorHex() {
        return categoryColorHex;
    }

    public long getSpentCents() {
        return spentCents;
    }

    public long getBaseLimitCents() {
        return baseLimitCents;
    }

    public long getEffectiveLimitCents() {
        return effectiveLimitCents;
    }

    public int getPercentage() {
        return percentage;
    }
}
