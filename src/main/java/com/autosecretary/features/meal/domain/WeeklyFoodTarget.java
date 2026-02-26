package com.autosecretary.features.meal.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * DGE-Wochenbedarf pro Lebensmittelgruppe.
 * "Weekly" bezieht sich auf die DGE-Basis, nicht auf die Periodenlaenge.
 * Wird sowohl fuer 7-Tage-Wochenziele als auch periodenlaengen-skaliert genutzt.
 */
public class WeeklyFoodTarget {

    public Long id;
    public String periodKey;

    // Ziel-Gramm pro FoodGroup
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

    // Geplant/Erfuellt pro FoodGroup
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
            case OTHER -> {} // ignorieren
        }
    }

    /**
     * Verbleibender Bedarf pro FoodGroup (Ziel minus Geplant, min 0).
     */
    public Map<Ingredient.FoodGroup, Integer> toRemainingMap() {
        Map<Ingredient.FoodGroup, Integer> result = new EnumMap<>(Ingredient.FoodGroup.class);
        for (Ingredient.FoodGroup fg : Ingredient.FoodGroup.values()) {
            int remaining = Math.max(0, getTargetFor(fg) - getPlannedFor(fg));
            result.put(fg, remaining);
        }
        return result;
    }

    public void setTargetFor(Ingredient.FoodGroup group, int grams) {
        switch (group) {
            case GRAIN -> grainGrams = grams;
            case POTATO -> potatoGrams = grams;
            case VEGETABLE -> vegetableGrams = grams;
            case FRUIT -> fruitGrams = grams;
            case DAIRY -> dairyGrams = grams;
            case MEAT -> meatGrams = grams;
            case FISH -> fishGrams = grams;
            case EGG -> eggGrams = grams;
            case FAT -> fatGrams = grams;
            case LEGUME -> legumeGrams = grams;
            case NUT -> nutGrams = grams;
            case OTHER -> {} // ignorieren
        }
    }
}
