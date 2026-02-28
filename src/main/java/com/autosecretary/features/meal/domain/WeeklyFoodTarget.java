package com.autosecretary.features.meal.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * DGE-based weekly food group targets and planned amounts for a household.
 *
 * <p>Targets and planned amounts are stored per {@link Ingredient.FoodGroup} in two EnumMaps.
 * Use the dispatch methods ({@link #getTargetFor}, {@link #getPlannedFor}, {@link #addPlanned},
 * {@link #setTargetFor}, {@link #setPlannedFor}) to read and write values by food group.
 *
 * <p>"Weekly" refers to the DGE reference window, not a fixed calendar week. The same class is
 * used for any period length; callers produce the appropriate {@code periodKey} themselves.
 *
 * <p>Note: {@code OTHER} food group always has a target of 0 and accumulates no planned amount.
 */
public class WeeklyFoodTarget {

    public Long id;
    public String periodKey;            // ISO date string used as storage key (e.g. "2026-02-14"); opaque to this class

    private final EnumMap<Ingredient.FoodGroup, Integer> targets = new EnumMap<>(Ingredient.FoodGroup.class);
    private final EnumMap<Ingredient.FoodGroup, Integer> planned = new EnumMap<>(Ingredient.FoodGroup.class);

    /** Returns the target grams for the given food group (0 for OTHER). */
    public int getTargetFor(Ingredient.FoodGroup group) {
        return targets.getOrDefault(group, 0);
    }

    /** Returns the planned grams for the given food group (0 for OTHER). */
    public int getPlannedFor(Ingredient.FoodGroup group) {
        return planned.getOrDefault(group, 0);
    }

    /** Sets the target grams for the given food group. No-op for OTHER. */
    public void setTargetFor(Ingredient.FoodGroup group, int grams) {
        if (group != Ingredient.FoodGroup.OTHER) targets.put(group, grams);
    }

    /** Sets the planned grams for the given food group. No-op for OTHER. */
    public void setPlannedFor(Ingredient.FoodGroup group, int grams) {
        if (group != Ingredient.FoodGroup.OTHER) planned.put(group, grams);
    }

    /** Adds grams to the planned amount for the given food group. No-op for OTHER. */
    public void addPlanned(Ingredient.FoodGroup group, int grams) {
        if (group != Ingredient.FoodGroup.OTHER) {
            planned.merge(group, grams, Integer::sum);
        }
    }

    /** Returns remaining need per food group (target minus planned, minimum 0). */
    public Map<Ingredient.FoodGroup, Integer> toRemainingMap() {
        Map<Ingredient.FoodGroup, Integer> result = new EnumMap<>(Ingredient.FoodGroup.class);
        for (Ingredient.FoodGroup fg : Ingredient.FoodGroup.values()) {
            result.put(fg, Math.max(0, getTargetFor(fg) - getPlannedFor(fg)));
        }
        return result;
    }
}
