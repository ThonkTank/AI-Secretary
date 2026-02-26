package com.autosecretary.features.budget.ui.state;

public class BudgetLimitBar {
    private final String categoryId;
    private final String categoryName;
    private final String categoryColorHex;
    private final long spentCents;
    private final long baseLimitCents;      // The configured monthly spending limit
    private final long effectiveLimitCents; // Limit after applying rollover carryover (may exceed base)
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
