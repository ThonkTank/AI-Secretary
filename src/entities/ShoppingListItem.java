package entities;

import java.time.LocalDateTime;

/**
 * Ein Eintrag in der Einkaufsliste.
 */
public class ShoppingListItem {

    public Long id;
    public String weekKey;                   // "2026-W06" (Jahr-Woche)
    public Long ingredientId;                // FK zu Ingredient (null bei Freitext)
    public String ingredientName;            // Anzeigename
    public double amount;                    // Menge
    public String unit;                      // Einheit

    public boolean isPurchased;              // Abgehakt?
    public LocalDateTime purchasedAt;
    public int actualPriceCents;             // Optional: tatsächlicher Preis

    public Long sourceRecipeId;              // Von welchem Rezept (für Nachvollziehbarkeit)

    // Für Gruppierung
    public String foodGroupLabel;            // z.B. "Fleisch", "Gemüse" (denormalisiert)

    // Für Single-Store-Optimierung (Phase 3C)
    public double neededAmount;              // Tatsächlich benötigte Menge (vor Aufrundung)
    public double excessAmount;              // Überschuss durch Packungsgrößen
    public String suggestedStore;            // Empfohlener Laden (z.B. "Edeka")

    // Builder
    public static class Builder {
        private final ShoppingListItem item = new ShoppingListItem();

        public Builder(String weekKey, String ingredientName, double amount, String unit) {
            item.weekKey = weekKey;
            item.ingredientName = ingredientName;
            item.amount = amount;
            item.unit = unit;
        }

        public Builder ingredientId(Long v) { item.ingredientId = v; return this; }
        public Builder sourceRecipe(Long v) { item.sourceRecipeId = v; return this; }
        public Builder foodGroup(String v) { item.foodGroupLabel = v; return this; }
        public Builder neededAmount(double v) { item.neededAmount = v; return this; }
        public Builder excessAmount(double v) { item.excessAmount = v; return this; }
        public Builder suggestedStore(String v) { item.suggestedStore = v; return this; }

        public ShoppingListItem build() { return item; }
    }

    // ============== UTILITY ==============

    /**
     * Markiert den Artikel als gekauft.
     */
    public void markPurchased() {
        this.isPurchased = true;
        this.purchasedAt = LocalDateTime.now();
    }

    /**
     * Markiert den Artikel als nicht gekauft.
     */
    public void markNotPurchased() {
        this.isPurchased = false;
        this.purchasedAt = null;
    }

    /**
     * Toggle-Funktion.
     */
    public void togglePurchased() {
        if (isPurchased) {
            markNotPurchased();
        } else {
            markPurchased();
        }
    }

    /**
     * Formatiert Menge + Einheit für Anzeige.
     */
    public String getFormattedAmount() {
        if (amount == (int) amount) {
            return (int) amount + " " + unit;
        }
        return String.format("%.1f %s", amount, unit);
    }

    /**
     * Formatiert den Überschuss für Anzeige.
     * @return z.B. "(+50 g Überschuss)" oder leerer String wenn kein Überschuss
     */
    public String getFormattedExcess() {
        if (excessAmount <= 0) return "";
        if (excessAmount == (int) excessAmount) {
            return "(+" + (int) excessAmount + " " + unit + " Überschuss)";
        }
        return String.format("(+%.1f %s Überschuss)", excessAmount, unit);
    }
}
