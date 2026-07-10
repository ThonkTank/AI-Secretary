package com.autosecretary.features.budget.application.overview;

public record BudgetLimitOverviewItem(
        String categoryId,
        String categoryName,
        String categoryColorHex,
        long spentCents,
        long baseLimitCents,
        long effectiveLimitCents) {
}
