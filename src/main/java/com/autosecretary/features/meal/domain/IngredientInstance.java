package com.autosecretary.features.meal.domain;

import java.time.LocalDate;

/**
 * Generische Instanz einer Zutat — verwendet fuer Vorrat (Pantry) und Einkaufsliste (Shopping).
 *
 * Diskriminator: purchaseDate != null → Vorrat, purchaseDate == null → Einkaufsliste.
 * Alle weiteren Daten (Name, Unit, Ablaufdatum, Lagerort) werden zur Laufzeit
 * aus der verknuepften Ingredient abgeleitet.
 */
public class IngredientInstance {

    public Long id;
    public long ingredientId;
    public double amount;
    public LocalDate purchaseDate;    // null = Einkaufsliste, gesetzt = Vorrat
    public boolean isPurchased;       // Nur fuer Einkaufsliste relevant

    public enum StorageLocation {
        FRIDGE("Kühlschrank", "🧊"),
        FREEZER("Tiefkühler", "❄️"),
        PANTRY("Vorratskammer", "🏠");

        public final String label;
        public final String icon;

        StorageLocation(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public void togglePurchased() { isPurchased = !isPurchased; }

    // Builder
    public static class Builder {
        private final IngredientInstance i = new IngredientInstance();

        public Builder(long ingredientId, double amount) {
            i.ingredientId = ingredientId;
            i.amount = amount;
        }

        public Builder purchaseDate(LocalDate v) { i.purchaseDate = v; return this; }
        public Builder purchased(boolean v) { i.isPurchased = v; return this; }

        public static Builder forShopping(long ingredientId, double amount) {
            return new Builder(ingredientId, amount);
        }

        public static Builder forPantry(long ingredientId, double amount, LocalDate purchaseDate) {
            return new Builder(ingredientId, amount).purchaseDate(purchaseDate);
        }

        public IngredientInstance build() { return i; }
    }
}
