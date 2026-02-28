package com.autosecretary.features.meal.domain.internal;

/** Shared formatting for meal item amounts (used by PantryItem and ShoppingListItem). */
public final class MealAmountFormat {

    private MealAmountFormat() {}

    /**
     * Formats a measurement amount and unit into a human-readable string.
     *
     * <p>Whole numbers display without a decimal point (e.g., "500 g"), while fractional amounts
     * display with exactly 1 decimal place (e.g., "1.5 kg"). Due to floating-point precision limits,
     * values very close to whole numbers may still display with a decimal point.</p>
     *
     * @param amount the numeric quantity (e.g., 500.0, 1.5)
     * @param unit the unit string (e.g., "g", "kg", "ml")
     * @return formatted string with amount and unit separated by a space
     */
    public static String format(double amount, String unit) {
        if (amount == (int) amount) return (int) amount + " " + unit;
        return String.format("%.1f %s", amount, unit);
    }
}
