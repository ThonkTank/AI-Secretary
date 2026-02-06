package entities;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Berechneter Wochenbedarf pro Lebensmittelgruppe für den gesamten Haushalt.
 */
public class WeeklyFoodTarget {

    public Long id;
    public String weekKey;                   // "2026-W06"

    // Berechnete Wochenmengen für Haushalt (in Gramm)
    public int grainGrams;
    public int potatoGrams;
    public int vegetableGrams;
    public int fruitGrams;
    public int dairyGrams;
    public int meatGrams;
    public int fishGrams;
    public int eggGrams;
    public int fatGrams;
    public int legumeGrams;
    public int nutGrams;

    // Tracking: wieviel davon wurde eingeplant
    public int grainPlanned;
    public int potatoPlanned;
    public int vegetablePlanned;
    public int fruitPlanned;
    public int dairyPlanned;
    public int meatPlanned;
    public int fishPlanned;
    public int eggPlanned;
    public int fatPlanned;
    public int legumePlanned;
    public int nutPlanned;

    // Builder
    public static class Builder {
        private final WeeklyFoodTarget target = new WeeklyFoodTarget();

        public Builder(String weekKey) {
            target.weekKey = weekKey;
        }

        public WeeklyFoodTarget build() { return target; }
    }

    // ============== CALCULATION ==============

    /**
     * Berechnet den Wochenbedarf aus allen aktiven Haushaltsmitgliedern.
     */
    public static WeeklyFoodTarget calculate(String weekKey, List<HouseholdMember> members) {
        WeeklyFoodTarget target = new WeeklyFoodTarget();
        target.weekKey = weekKey;

        for (HouseholdMember m : members) {
            if (!m.isActive) continue;

            double factor = m.getFoodFactor();

            target.grainGrams += (int) (Ingredient.FoodGroup.GRAIN.weeklyGramsPerAdult * factor);
            target.potatoGrams += (int) (Ingredient.FoodGroup.POTATO.weeklyGramsPerAdult * factor);
            target.vegetableGrams += (int) (Ingredient.FoodGroup.VEGETABLE.weeklyGramsPerAdult * factor);
            target.fruitGrams += (int) (Ingredient.FoodGroup.FRUIT.weeklyGramsPerAdult * factor);
            target.dairyGrams += (int) (Ingredient.FoodGroup.DAIRY.weeklyGramsPerAdult * factor);
            target.meatGrams += (int) (Ingredient.FoodGroup.MEAT.weeklyGramsPerAdult * factor);
            target.fishGrams += (int) (Ingredient.FoodGroup.FISH.weeklyGramsPerAdult * factor);
            target.eggGrams += (int) (Ingredient.FoodGroup.EGG.weeklyGramsPerAdult * factor);
            target.fatGrams += (int) (Ingredient.FoodGroup.FAT.weeklyGramsPerAdult * factor);
            target.legumeGrams += (int) (Ingredient.FoodGroup.LEGUME.weeklyGramsPerAdult * factor);
            target.nutGrams += (int) (Ingredient.FoodGroup.NUT.weeklyGramsPerAdult * factor);
        }

        return target;
    }

    // ============== UTILITY ==============

    /**
     * Gibt den Zielwert für eine Lebensmittelgruppe zurück.
     */
    public int getTargetFor(Ingredient.FoodGroup group) {
        return switch (group) {
            case GRAIN -> grainGrams;
            case POTATO -> potatoGrams;
            case VEGETABLE -> vegetableGrams;
            case FRUIT -> fruitGrams;
            case DAIRY -> dairyGrams;
            case MEAT -> meatGrams;
            case FISH -> fishGrams;
            case EGG -> eggGrams;
            case FAT -> fatGrams;
            case LEGUME -> legumeGrams;
            case NUT -> nutGrams;
            case OTHER -> 0;
        };
    }

    /**
     * Gibt den geplanten Wert für eine Lebensmittelgruppe zurück.
     */
    public int getPlannedFor(Ingredient.FoodGroup group) {
        return switch (group) {
            case GRAIN -> grainPlanned;
            case POTATO -> potatoPlanned;
            case VEGETABLE -> vegetablePlanned;
            case FRUIT -> fruitPlanned;
            case DAIRY -> dairyPlanned;
            case MEAT -> meatPlanned;
            case FISH -> fishPlanned;
            case EGG -> eggPlanned;
            case FAT -> fatPlanned;
            case LEGUME -> legumePlanned;
            case NUT -> nutPlanned;
            case OTHER -> 0;
        };
    }

    /**
     * Addiert geplante Mengen zu einer Lebensmittelgruppe.
     */
    public void addPlanned(Ingredient.FoodGroup group, int grams) {
        switch (group) {
            case GRAIN -> grainPlanned += grams;
            case POTATO -> potatoPlanned += grams;
            case VEGETABLE -> vegetablePlanned += grams;
            case FRUIT -> fruitPlanned += grams;
            case DAIRY -> dairyPlanned += grams;
            case MEAT -> meatPlanned += grams;
            case FISH -> fishPlanned += grams;
            case EGG -> eggPlanned += grams;
            case FAT -> fatPlanned += grams;
            case LEGUME -> legumePlanned += grams;
            case NUT -> nutPlanned += grams;
            case OTHER -> {} // Ignorieren
        }
    }

    /**
     * Berechnet die Erfüllungsquote für eine Lebensmittelgruppe.
     * @return Prozent (0.0 bis 1.0+)
     */
    public double getProgressFor(Ingredient.FoodGroup group) {
        int target = getTargetFor(group);
        if (target == 0) return 1.0;
        return (double) getPlannedFor(group) / target;
    }

    /**
     * Berechnet verbleibenden Bedarf für eine Lebensmittelgruppe.
     */
    public int getRemainingFor(Ingredient.FoodGroup group) {
        return Math.max(0, getTargetFor(group) - getPlannedFor(group));
    }

    /**
     * Konvertiert zu Map für Algorithmen.
     */
    public Map<Ingredient.FoodGroup, Integer> toRemainingMap() {
        Map<Ingredient.FoodGroup, Integer> map = new HashMap<>();
        for (Ingredient.FoodGroup g : Ingredient.FoodGroup.values()) {
            if (g != Ingredient.FoodGroup.OTHER) {
                map.put(g, getRemainingFor(g));
            }
        }
        return map;
    }
}
