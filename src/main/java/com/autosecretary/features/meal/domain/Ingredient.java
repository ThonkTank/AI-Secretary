package com.autosecretary.features.meal.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A food ingredient with nutritional values, DGE food group classification, and optional
 * store-specific packaging information.
 *
 * <p><strong>Nutrition encoding (×10 fixed-point):</strong> Macro fields ({@code proteinPer100},
 * {@code carbsPer100}, {@code fatPer100}, {@code fiberPer100}) store one decimal place as an
 * integer scaled by 10. Example: 12.5 g protein per 100 g → {@code proteinPer100 = 125}.
 * {@code caloriesPer100} is plain kcal (no ×10 scaling).
 *
 * <p><strong>Unit handling:</strong> {@code defaultUnit} is the display unit (e.g. "g", "ml",
 * "Stück"). {@code gramsPerUnit} converts non-gram units to grams for DGE calculations —
 * e.g. 1 Stück Ei = 60 g → {@code gramsPerUnit = 60}. For "g" and "ml", {@code gramsPerUnit = 1}.
 *
 * <p>See also {@code meal/domain/README.md} for the full nutrition encoding description.
 */
public class Ingredient {

    /**
     * A store-specific packaging variant of an ingredient.
     * For example, "Rewe Fleischtomaten 500 g" is one StorePackage for ingredient "Tomaten".
     * Multiple StorePackages allow tracking which shop and package size is cheapest.
     */
    public static class StorePackage {
        public String storeName;
        public String unit;
        public int packageAmount;
        public Integer priceCents;
        public LocalDate lastPurchased;
    }

    public String id;
    public String name;
    public FoodGroup foodGroup;
    public String defaultUnit;          // "g", "ml", "Stück"
    public int gramsPerUnit;            // Gewicht pro defaultUnit
    public int caloriesPer100;          // plain kcal per 100g (no ×10 scaling — different from macros below)
    public int proteinPer100;           // ×10 fixed-point g per 100g: 125 = 12.5g
    public int carbsPer100;             // ×10 fixed-point g per 100g: 125 = 12.5g
    public int fatPer100;               // ×10 fixed-point g per 100g: 125 = 12.5g
    public int fiberPer100;             // ×10 fixed-point g per 100g: 125 = 12.5g
    public int shelfLifeDays;           // shelf life in days from purchase date (used by ShelfLifeService)
    public boolean requiresRefrigeration;
    public boolean isWholeUnit;         // true = only whole numbers make sense (e.g. eggs)
    public boolean isPerishable;        // true = shelf life < 7 days
    public List<StorePackage> storePackages = new ArrayList<>();

    /**
     * Converts an amount in this ingredient's unit to grams, for DGE food-group tracking.
     *
     * <p>"g" and "ml" are treated as equivalent to grams (returned as-is). Any other unit
     * is multiplied by {@code gramsPerUnit} (e.g. 3 Stück × 60 g/Stück = 180 g).
     *
     * @param amount the quantity to convert
     * @param unit   the unit of {@code amount} (e.g. "g", "ml", "Stück")
     * @return equivalent mass in grams
     */
    public double getFoodGroupGrams(double amount, String unit) {
        if ("g".equals(unit) || "ml".equals(unit)) return amount;
        return amount * gramsPerUnit;
    }

    /**
     * Finds the best matching StorePackage for a preferred store and unit.
     *
     * <p>First filters packages by {@code unit} (if not null), then prefers {@code preferredStore}.
     * If no package matches the preferred store, returns the first matching package.
     * Returns null if no packages match at all.
     *
     * @param preferredStore store name to prefer; may be null (skips store preference)
     * @param unit           required unit; null means any unit is acceptable
     * @return best matching StorePackage, or null if none found
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
     * Updates the {@code lastPurchased} date for all StorePackages belonging to {@code store}.
     * Has no effect if {@code store} is null or no packages match.
     *
     * @param store the store name to update (exact match)
     * @param date  the purchase date to record
     */
    public void updateLastPurchased(String store, LocalDate date) {
        if (store == null) return;
        for (StorePackage pkg : storePackages) {
            if (store.equals(pkg.storeName)) {
                pkg.lastPurchased = date;
            }
        }
    }

    /**
     * DGE food group classification with reference weekly intake per adult (in grams).
     *
     * <p>{@code weeklyGramsPerAdult} is the DGE reference value for a standard 2000 kcal/day adult.
     * {@code OTHER} has 0 grams — it is a catch-all group that does not contribute to DGE targets.
     * {@code label} and {@code icon} are German display strings used in the UI.
     *
     * <p>Reference: <a href="https://www.dge.de">DGE — Deutsche Gesellschaft für Ernährung</a>.
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
