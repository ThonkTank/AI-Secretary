package entities;

/**
 * Repräsentiert eine Zutat mit Nährwerten und Lebensmittelgruppe.
 * ~200 Zutaten werden vorseeded für die App.
 */
public class Ingredient {

    public Long id;
    public String name;                      // "Hähnchenbrust"
    public FoodGroup foodGroup;              // MEAT, VEGETABLE, etc.
    public String defaultUnit;               // "g", "ml", "Stück"
    public int gramsPerUnit;                 // z.B. 1 Ei = 60g, 1 Scheibe Brot = 50g

    // Nährwerte pro 100g/100ml
    public int caloriesPer100;               // kcal
    public int proteinPer100;                // g × 10 (für Dezimalgenauigkeit)
    public int carbsPer100;                  // g × 10
    public int fatPer100;                    // g × 10
    public int fiberPer100;                  // g × 10

    public int shelfLifeDays;                // Haltbarkeit nach Kauf (0 = unbegrenzt)
    public boolean requiresRefrigeration;

    // Einkaufs-Eigenschaften
    public boolean isWholeUnit;              // true = Eier, Paprika (nur ganze Einheiten kaufbar)
    public boolean isPerishable;             // true = verderblich (sollte bis Wochenende verbraucht werden)

    // Lebensmittelgruppen mit DGE-Wochenempfehlungen pro Erwachsener
    public enum FoodGroup {
        GRAIN(1750, "Getreide"),        // g/Woche (250g/Tag Brot/Getreide)
        POTATO(1400, "Kartoffeln"),     // g/Woche (200g/Tag)
        VEGETABLE(2800, "Gemüse"),      // g/Woche (400g/Tag)
        FRUIT(1750, "Obst"),            // g/Woche (250g/Tag)
        DAIRY(1750, "Milchprodukte"),   // g/Woche (250g/Tag Milch/Joghurt/Käse)
        MEAT(300, "Fleisch"),           // g/Woche (max 300-600g)
        FISH(200, "Fisch"),             // g/Woche (1-2x pro Woche)
        EGG(210, "Eier"),               // g/Woche (~3 Eier à 70g)
        FAT(175, "Öle/Fette"),          // g/Woche (25g/Tag Öl/Butter)
        LEGUME(500, "Hülsenfrüchte"),   // g/Woche (optional, 1-2x)
        NUT(175, "Nüsse"),              // g/Woche (25g/Tag)
        OTHER(0, "Sonstiges");          // Gewürze, Saucen (nicht in Bedarfsrechnung)

        public final int weeklyGramsPerAdult;
        public final String label;

        FoodGroup(int weeklyGrams, String label) {
            this.weeklyGramsPerAdult = weeklyGrams;
            this.label = label;
        }
    }

    // Builder
    public static class Builder {
        private final Ingredient ing = new Ingredient();

        public Builder(String name, FoodGroup foodGroup) {
            ing.name = name;
            ing.foodGroup = foodGroup;
            ing.defaultUnit = "g";
            ing.gramsPerUnit = 100;
        }

        public Builder unit(String unit, int gramsPerUnit) {
            ing.defaultUnit = unit;
            ing.gramsPerUnit = gramsPerUnit;
            return this;
        }

        public Builder nutrition(int calories, int protein, int carbs, int fat, int fiber) {
            ing.caloriesPer100 = calories;
            ing.proteinPer100 = protein;
            ing.carbsPer100 = carbs;
            ing.fatPer100 = fat;
            ing.fiberPer100 = fiber;
            return this;
        }

        public Builder shelfLife(int days) { ing.shelfLifeDays = days; return this; }
        public Builder refrigerated() { ing.requiresRefrigeration = true; return this; }
        public Builder wholeUnit() { ing.isWholeUnit = true; return this; }
        public Builder perishable() { ing.isPerishable = true; return this; }

        public Ingredient build() { return ing; }
    }

    // ============== UTILITY ==============

    /**
     * Konvertiert eine Menge zu Gramm für die Lebensmittelgruppen-Berechnung.
     * @param amount Menge (z.B. 2 Stück)
     * @param unit Einheit (z.B. "Stück", "g", "ml")
     * @return Gramm für die Lebensmittelgruppe
     */
    public int getFoodGroupGrams(double amount, String unit) {
        // Wenn bereits in Gramm oder ml, direkt verwenden
        if ("g".equals(unit) || "ml".equals(unit)) {
            return (int) amount;
        }
        // Sonst: Einheit → Gramm umrechnen
        return (int) (amount * gramsPerUnit);
    }

    /**
     * Berechnet Kalorien für eine gegebene Menge.
     * @param amount Menge
     * @param unit Einheit
     * @return Kalorien
     */
    public int calculateCalories(double amount, String unit) {
        int grams = getFoodGroupGrams(amount, unit);
        return (caloriesPer100 * grams) / 100;
    }

    /**
     * Berechnet Protein für eine gegebene Menge.
     * @return Protein in Gramm × 10 (für Dezimalgenauigkeit)
     */
    public int calculateProtein(double amount, String unit) {
        int grams = getFoodGroupGrams(amount, unit);
        return (proteinPer100 * grams) / 100;
    }
}
