package com.autosecretary.features.meal.domain;

import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stateless service that calculates DGE-based weekly food group targets for a household.
 *
 * <p>The calculation scales the DGE reference portions (defined on each {@link Ingredient.FoodGroup}
 * as {@code weeklyGramsPerAdult}) by each active member's personal energy factor. The energy factor
 * is the ratio of the member's TDEE to the DGE baseline of 2000 kcal/day — computed by
 * {@link com.autosecretary.features.meal.domain.internal.HouseholdEnergyService#calculateDgeFoodFactor}.
 *
 * <p>Usage: call the static {@link #calculate} method directly — do not instantiate this class.
 *
 * <p>See also {@code meal/domain/README.md} for the DGE concept overview.
 */
public class WeeklyFoodTargetService {

    /**
     * Calculates a {@link WeeklyFoodTarget} for the household based on the active members' energy needs.
     *
     * <p>Only members with {@code isActive == true} contribute to the target. Inactive members and
     * null entries in {@code members} are silently skipped. If {@code members} is null or empty,
     * the returned target has zero grams for every food group.
     *
     * @param periodKey    opaque ISO date string used as storage key (e.g. {@code "2026-02-14"})
     * @param members      all household members (active and inactive); may be null or empty
     * @param referenceDate date used to calculate each member's age and TDEE; pass a fixed date
     *                     (not {@code LocalDate.now()}) when deterministic results are needed
     * @return a new {@link WeeklyFoodTarget} with gram targets set for each {@link Ingredient.FoodGroup}
     */
    public static WeeklyFoodTarget calculate(String periodKey, List<HouseholdMember> members, LocalDate referenceDate) {
        WeeklyFoodTarget target = new WeeklyFoodTarget();
        target.periodKey = periodKey;

        double totalFactor = Objects.requireNonNullElse(members, Collections.<HouseholdMember>emptyList()).stream()
                .filter(m -> m != null && m.isActive)
                .mapToDouble(m -> HouseholdEnergyService.calculateDgeFoodFactor(m, referenceDate))
                .sum();

        for (Ingredient.FoodGroup group : Ingredient.FoodGroup.values()) {
            int grams = (int) (group.weeklyGramsPerAdult * totalFactor);
            target.setTargetFor(group, grams);
        }
        return target;
    }

    private WeeklyFoodTargetService() {}
}
