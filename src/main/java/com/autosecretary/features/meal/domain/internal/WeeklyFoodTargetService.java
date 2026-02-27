package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.HouseholdMember;
import com.autosecretary.features.meal.domain.Ingredient;
import com.autosecretary.features.meal.domain.WeeklyFoodTarget;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Berechnet DGE-Wochenziele anhand der aktiven Haushaltsmitglieder.
 */
public class WeeklyFoodTargetService {

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
