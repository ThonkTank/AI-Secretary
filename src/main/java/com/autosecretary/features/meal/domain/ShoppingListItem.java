package com.autosecretary.features.meal.domain;

import com.autosecretary.features.meal.domain.internal.MealAmountFormat;

/**
 * A shopping list entry for a specific ingredient and shopping period.
 *
 * <p>Three amount fields track the packaging rounding applied by {@link ShoppingPackagingService}:
 * <ul>
 *   <li>{@code neededAmount} — the raw amount actually required</li>
 *   <li>{@code excessAmount} — leftover from rounding up to whole packages</li>
 *   <li>{@code amount} — total to buy ({@code neededAmount + excessAmount})</li>
 * </ul>
 *
 * <p>{@code ingredientName} and {@code foodGroupLabel} are denormalized for display.
 *
 * <p>{@code periodKey} is an ISO date string identifying the shopping day
 * (e.g. {@code "2026-02-14"}). Items for different shopping days have different keys;
 * use {@code PantryRepository#getShoppingListItems(periodKey)} to retrieve items for one day.
 */
public class ShoppingListItem {

    public Long id;
    public long ingredientId;
    public String ingredientName;       // denormalized from Ingredient for display
    public double amount;               // total to buy = neededAmount + excessAmount (rounded up to full package)
    public double neededAmount;         // raw amount actually required
    public double excessAmount;         // leftover from package rounding
    public String unit;
    public String foodGroupLabel;       // denormalized from Ingredient.FoodGroup for display
    public String suggestedStore;
    public boolean isPurchased;
    public String periodKey;            // ISO date string for the shopping day (e.g. "2026-02-14")
    public int estimatedPriceCents;

    // Builder
    public static class Builder {
        private final ShoppingListItem i = new ShoppingListItem();

        public Builder(long ingredientId, String ingredientName, double needed, String unit) {
            i.ingredientId = ingredientId;
            i.ingredientName = ingredientName;
            i.neededAmount = needed;
            i.amount = needed;
            i.unit = unit;
        }

        public Builder excess(double v) { i.excessAmount = v; i.amount = i.neededAmount + v; return this; }
        public Builder foodGroup(String v) { i.foodGroupLabel = v; return this; }
        public Builder store(String v) { i.suggestedStore = v; return this; }
        public Builder periodKey(String v) { i.periodKey = v; return this; }
        public Builder price(int cents) { i.estimatedPriceCents = cents; return this; }

        public ShoppingListItem build() { return i; }
    }

    public void markPurchased() { isPurchased = true; }

    public void togglePurchased() { isPurchased = !isPurchased; }

    public String getFormattedAmount() {
        return MealAmountFormat.format(amount, unit);
    }

    public String getFormattedExcess() {
        if (excessAmount <= 0) return "";
        return "(+" + MealAmountFormat.format(excessAmount, unit) + " Überschuss)";
    }
}
