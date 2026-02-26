package com.autosecretary.features.budget.domain;

public record CategorySpendSummary(
        String categoryId,
        String categoryName,
        String categoryIcon,
        String categoryColorHex,
        long spentCents,
        long limitAmountCents
) {}
