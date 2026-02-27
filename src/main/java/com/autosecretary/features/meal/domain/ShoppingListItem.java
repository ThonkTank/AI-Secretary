package com.autosecretary.features.meal.domain;

/**
 * Einkaufslisten-Eintrag fuer eine Einkaufsperiode.
 */
public class ShoppingListItem {

    public Long id;
    public long ingredientId;
    public String ingredientName;       // Denormalized
    public double amount;               // Gesamtmenge (aufgerundet auf Packung)
    public double neededAmount;          // Tatsaechlich benoetigte Menge
    public double excessAmount;          // Überschuss durch Packungsgroessen
    public String unit;
    public String foodGroupLabel;       // Denormalized
    public String suggestedStore;
    public boolean isPurchased;
    public String periodKey;            // Einkaufstag als ISO-String (z.B. "2026-02-14")
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
        if (excessAmount == (int) excessAmount)
            return "(+" + (int) excessAmount + " " + unit + " Überschuss)";
        return String.format("(+%.1f %s Überschuss)", excessAmount, unit);
    }
}
