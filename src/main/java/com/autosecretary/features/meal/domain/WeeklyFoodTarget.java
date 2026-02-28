package com.autosecretary.features.meal.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * DGE-based weekly food group targets and planned amounts for a household.
 *
 * <p>Each {@link Ingredient.FoodGroup} has two parallel integer fields:
 * <ul>
 *   <li>{@code *Grams} — the target amount in grams (computed by {@link WeeklyFoodTargetService})</li>
 *   <li>{@code *Planned} — the amount already planned/consumed in grams (incremented as meals are added)</li>
 * </ul>
 *
 * <p>"Weekly" refers to the DGE reference window, not a fixed calendar week. The same class is
 * used for any period length; callers produce the appropriate {@code periodKey} themselves.
 *
 * <p>Use the dispatch methods ({@link #getTargetFor}, {@link #getPlannedFor}, {@link #addPlanned},
 * {@link #setTargetFor}) to access fields by {@link Ingredient.FoodGroup} enum value rather than
 * referencing individual fields directly — this keeps callers decoupled from the flat field structure.
 *
 * <p>Note: {@code OTHER} food group always has a target of 0 and accumulates no planned amount.
 */
public class WeeklyFoodTarget {

    public Long id;
    public String periodKey;            // ISO date string used as storage key (e.g. "2026-02-14"); opaque to this class

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
