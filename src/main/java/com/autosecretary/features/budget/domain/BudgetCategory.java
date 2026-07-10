package com.autosecretary.features.budget.domain;

public record BudgetCategory(
        String id,
        String name,
        TransactionDirection direction,
        String icon,
        String colorHex,
        boolean archived
) {
    public static final String DEFAULT_ICON = "🏷️";
    public static final String DEFAULT_COLOR_HEX = "#9E9E9E";

    public static BudgetCategory create(String name, TransactionDirection direction, String icon, String colorHex) {
        return new BudgetCategory(
                null,
                name,
                direction,
                icon != null ? icon : DEFAULT_ICON,
                colorHex != null ? colorHex : DEFAULT_COLOR_HEX,
                false
        );
    }
}
