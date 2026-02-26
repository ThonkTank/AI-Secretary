package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Zutat mit Naehrwerten und Lebensmittelgruppe (DGE).
 * Naehrwerte sind pro 100g, ganzzahlig (×10 fuer eine Dezimalstelle).
 * StorePackages sind konkrete Laden-Varianten (z.B. "Rewe Fleischtomaten 350g").
 */
public class Ingredient {

    /**
     * Verpackungs-/Store-spezifische Variante einer Zutat.
     */
    public static class StorePackage {
        public String storeName;
        public String unit;
        public int packageAmount;
        public Integer priceCents;
        public LocalDate lastPurchased;
    }

    public Long id;
    public String name;
    public FoodGroup foodGroup;
    public String defaultUnit;          // "g", "ml", "Stück"
    public int gramsPerUnit;            // Gewicht pro defaultUnit
    public int caloriesPer100;          // kcal pro 100g
    public int proteinPer100;           // ×10: 125 = 12.5g
    public int carbsPer100;
    public int fatPer100;
    public int fiberPer100;
    public int shelfLifeDays;           // Haltbarkeit ab Kauf
    public boolean requiresRefrigeration;
    public boolean isWholeUnit;         // z.B. Eier (nur ganzzahlig skalierbar)
    public boolean isPerishable;        // Verderblich (< 7 Tage)
    public List<StorePackage> storePackages = new ArrayList<>();

    /**
     * Berechnet Gramm fuer eine gegebene Menge+Einheit.
     * g/ml: direkte Menge. Andere Einheiten: Menge * gramsPerUnit.
     */
    public double getFoodGroupGrams(double amount, String unit) {
        if ("g".equals(unit) || "ml".equals(unit)) return amount;
        return amount * gramsPerUnit;
    }

    /**
     * Findet beste Packung fuer gegebenen Store und Unit.
     * Bevorzugt preferredStore, sonst erste passende Packung.
     */
    public StorePackage findBestPackage(String preferredStore, String unit) {
        List<StorePackage> matching = unit != null
            ? storePackages.stream().filter(p -> unit.equals(p.unit)).toList()
            : storePackages;

        if (preferredStore != null) {
            for (StorePackage pkg : matching) {
                if (preferredStore.equals(pkg.storeName)) return pkg;
            }
        }
        return matching.isEmpty() ? null : matching.get(0);
    }

    /**
     * Aktualisiert lastPurchased fuer alle Packungen eines Stores.
     */
    public void updateLastPurchased(String store, LocalDate date) {
        for (StorePackage pkg : storePackages) {
            if (store.equals(pkg.storeName)) {
                pkg.lastPurchased = date;
            }
        }
    }

    /**
     * DGE-Lebensmittelgruppen mit woechentlichem Bedarf pro Erwachsener (Gramm).
     */
    public enum FoodGroup {
        GRAIN(1750, "Getreide", "🌾"),
        POTATO(1400, "Kartoffeln", "🥔"),
        VEGETABLE(2800, "Gemüse", "🥦"),
        FRUIT(1750, "Obst", "🍎"),
        DAIRY(1750, "Milchprodukte", "🥛"),
        MEAT(450, "Fleisch", "🥩"),
        FISH(200, "Fisch", "🐟"),
        EGG(250, "Eier", "🥚"),
        FAT(250, "Fette & Öle", "🫒"),
        LEGUME(500, "Hülsenfrüchte", "🫘"),
        NUT(175, "Nüsse & Samen", "🥜"),
        OTHER(0, "Sonstiges", "📦");

        public final int weeklyGramsPerAdult;
        public final String label;
        public final String icon;

        FoodGroup(int weeklyGramsPerAdult, String label, String icon) {
            this.weeklyGramsPerAdult = weeklyGramsPerAdult;
            this.label = label;
            this.icon = icon;
        }
    }

    // Builder
    public static class Builder {
        private final Ingredient i = new Ingredient();

        public Builder(String name, FoodGroup group) {
            i.name = name;
            i.foodGroup = group;
            i.defaultUnit = "g";
            i.gramsPerUnit = 1;
            i.storePackages = new ArrayList<>();
        }

        public Builder unit(String unit, int gramsPerUnit) {
            i.defaultUnit = unit;
            i.gramsPerUnit = gramsPerUnit;
            return this;
        }
        public Builder calories(int v) { i.caloriesPer100 = v; return this; }
        public Builder protein(int v) { i.proteinPer100 = v; return this; }
        public Builder carbs(int v) { i.carbsPer100 = v; return this; }
        public Builder fat(int v) { i.fatPer100 = v; return this; }
        public Builder fiber(int v) { i.fiberPer100 = v; return this; }
        public Builder shelfLife(int days) { i.shelfLifeDays = days; return this; }
        public Builder refrigerated() { i.requiresRefrigeration = true; return this; }
        public Builder wholeUnit() { i.isWholeUnit = true; return this; }
        public Builder perishable() { i.isPerishable = true; return this; }
        public Builder storePackages(List<StorePackage> v) { i.storePackages = v; return this; }

        public Ingredient build() { return i; }
    }
}
