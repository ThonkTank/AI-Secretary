package com.autosecretary.features.meal.domain.internal;

import com.autosecretary.features.meal.domain.HouseholdMember;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Funktionale Energie-Berechnungen fuer Haushaltsmitglieder.
 */
public class HouseholdEnergyService {

    private static final double DGE_REFERENCE_KCAL = 2000.0;

    // Mifflin-St Jeor formula coefficients (BMR calculation)
    private static final double MIFFLIN_WEIGHT_COEFF = 10.0;
    private static final double MIFFLIN_HEIGHT_COEFF = 6.25;
    private static final double MIFFLIN_AGE_COEFF = 5.0;
    private static final double FEMALE_BMR_INTERCEPT = -161.0;
    private static final double MALE_BMR_INTERCEPT = 5.0;
    // Gender.OTHER uses the average of male and female intercepts (reasonable approximation)
    private static final double OTHER_BMR_INTERCEPT = (FEMALE_BMR_INTERCEPT + MALE_BMR_INTERCEPT) / 2.0;

    public static int calculateAge(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        return Math.max(0, referenceDate.getYear() - member.birthYear);
    }

    /**
     * Grundumsatz nach Mifflin-St Jeor (kcal/Tag).
     */
    public static int calculateBmr(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        int age = calculateAge(member, referenceDate);
        double genderIntercept = switch (member.gender) {
            case FEMALE -> FEMALE_BMR_INTERCEPT;
            case MALE -> MALE_BMR_INTERCEPT;
            case OTHER -> OTHER_BMR_INTERCEPT;
        };
        // Mifflin-St Jeor: BMR = 10*weight + 6.25*height - 5*age + genderIntercept
        double weightTerm = MIFFLIN_WEIGHT_COEFF * member.weightKg;
        double heightTerm = MIFFLIN_HEIGHT_COEFF * member.heightCm;
        double ageTerm = MIFFLIN_AGE_COEFF * age;
        return (int) Math.round(weightTerm + heightTerm - ageTerm + genderIntercept);
    }

    public static int calculateTdee(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(member.activityLevel, "member.activityLevel must not be null");
        return (int) (calculateBmr(member, referenceDate) * member.activityLevel.factor);
    }

    public static double calculateDgeFoodFactor(HouseholdMember member, LocalDate referenceDate) {
        Objects.requireNonNull(member, "member must not be null");
        Objects.requireNonNull(referenceDate, "referenceDate must not be null");
        return calculateTdee(member, referenceDate) / DGE_REFERENCE_KCAL;
    }

    private HouseholdEnergyService() {}
}
