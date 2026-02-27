package com.autosecretary.features.meal.domain;

/** Shared formatting for meal item amounts (used by PantryItem and ShoppingListItem). */
public final class MealAmountFormat {

    private MealAmountFormat() {}

    /** Returns e.g. "500 g" or "1.5 kg" depending on whether the amount is a whole number. */
    public static String format(double amount, String unit) {
        if (amount == (int) amount) return (int) amount + " " + unit;
        return String.format("%.1f %s", amount, unit);
    }
}
