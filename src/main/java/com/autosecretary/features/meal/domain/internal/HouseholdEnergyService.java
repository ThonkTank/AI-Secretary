package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.HouseholdMember;

import java.time.LocalDate;

/**
 * Funktionale Energie-Berechnungen fuer Haushaltsmitglieder.
 */
public class HouseholdEnergyService {

    private static final double DGE_REFERENCE_KCAL = 2000.0;

    public int calculateAge(HouseholdMember member, LocalDate referenceDate) {
        if (member == null || referenceDate == null) {
            return 0;
        }
        return Math.max(0, referenceDate.getYear() - member.birthYear);
    }

    /**
     * Grundumsatz nach Mifflin-St Jeor (kcal/Tag).
     */
    public int calculateBmr(HouseholdMember member, LocalDate referenceDate) {
        if (member == null) {
            return 0;
        }
        int age = calculateAge(member, referenceDate);
        if (member.gender == HouseholdMember.Gender.FEMALE) {
            return (int) (10 * member.weightKg + 6.25 * member.heightCm - 5 * age - 161);
        }
        return (int) (10 * member.weightKg + 6.25 * member.heightCm - 5 * age + 5);
    }

    public int calculateTdee(HouseholdMember member, LocalDate referenceDate) {
        if (member == null || member.activityLevel == null) {
            return calculateBmr(member, referenceDate);
        }
        return (int) (calculateBmr(member, referenceDate) * member.activityLevel.factor);
    }

    public double calculateDgeFoodFactor(HouseholdMember member, LocalDate referenceDate) {
        return calculateTdee(member, referenceDate) / DGE_REFERENCE_KCAL;
    }
}
