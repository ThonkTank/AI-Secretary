package com.autosecretary.features.budget.ui.state;

public class BudgetLimitBar {
    private final String categoryId;
    private final String categoryName;
    private final String categoryColorHex;
    private final long spentCents;
    private final double baseLimitEuros;
    private final double effectiveLimitEuros;
    private final int percentage;

    public BudgetLimitBar(String categoryId, String categoryName, String categoryColorHex,
                          long spentCents, double baseLimitEuros, double effectiveLimitEuros) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.categoryColorHex = categoryColorHex;
        this.spentCents = spentCents;
        this.baseLimitEuros = baseLimitEuros;
        this.effectiveLimitEuros = effectiveLimitEuros;
        this.percentage = effectiveLimitEuros > 0
                ? (int) ((spentCents / 100.0) / effectiveLimitEuros * 100)
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

    public double getBaseLimitEuros() {
        return baseLimitEuros;
    }

    public double getEffectiveLimitEuros() {
        return effectiveLimitEuros;
    }

    public int getPercentage() {
        return percentage;
    }
}
