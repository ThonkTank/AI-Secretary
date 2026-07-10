package com.autosecretary.features.budget.domain;

import com.autosecretary.features.budget.data.entity.BudgetCategoryEntity;

public record BudgetCategory(
        String id,
        String name,
        TransactionDirection direction,
        String icon,
        String colorHex,
        boolean archived
) {
    public static BudgetCategory create(String name, TransactionDirection direction, String icon, String colorHex) {
        return new BudgetCategory(
                null,
                name,
                direction,
                icon != null ? icon : BudgetCategoryEntity.DEFAULT_ICON,
                colorHex != null ? colorHex : BudgetCategoryEntity.DEFAULT_COLOR_HEX,
                false
        );
    }
}
