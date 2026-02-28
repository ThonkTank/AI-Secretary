package com.autosecretary.features.meal.domain;

/**
 * The four meal types used for recipe classification and meal planning.
 *
 * <p>{@code label} and {@code icon} are German display strings used by the UI.
 * They are stored here as a convenience while no separate UI display-mapping exists.
 */
public enum MealType {
    BREAKFAST("Frühstück", "🍳"),
    LUNCH("Mittagessen", "🍽️"),
    DINNER("Abendessen", "🍲"),
    SNACK("Snack", "🍎");

    public final String label;
    public final String icon;

    MealType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }
}
