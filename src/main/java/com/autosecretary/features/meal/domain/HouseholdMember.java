package com.autosecretary.features.meal.domain;

import com.autosecretary.features.meal.domain.internal.HouseholdEnergyService;

import java.time.LocalDate;

/**
 * Haushaltsmitglied fuer DGE-Bedarfsberechnung und Meal-Planning.
 */
public class HouseholdMember {

    private static final HouseholdEnergyService ENERGY_SERVICE = new HouseholdEnergyService();

    public Long id;
    public String name;
    public int birthYear;
    public Gender gender;
    public int weightKg;
    public int heightCm;
    public int targetWeightKg;
    public ActivityLevel activityLevel;
    public boolean isActive;

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum ActivityLevel {
        SEDENTARY(1.2, "Sitzend"),
        LIGHT(1.375, "Leicht aktiv"),
        MODERATE(1.55, "Moderat aktiv"),
        ACTIVE(1.725, "Aktiv"),
        VERY_ACTIVE(1.9, "Sehr aktiv");

        public final double factor;
        public final String label;

        ActivityLevel(double factor, String label) {
            this.factor = factor;
            this.label = label;
        }
    }

    public int getAge() {
        return ENERGY_SERVICE.calculateAge(this, LocalDate.now());
    }

    /**
     * Grundumsatz nach Mifflin-St Jeor (kcal/Tag).
     */
    public int calculateBMR() {
        return ENERGY_SERVICE.calculateBmr(this, LocalDate.now());
    }

    /**
     * Tagesbedarf (Total Daily Energy Expenditure) in kcal.
     */
    public int calculateTDEE() {
        return ENERGY_SERVICE.calculateTdee(this, LocalDate.now());
    }

    /**
     * DGE-Skalierungsfaktor fuer Lebensmittelgruppen-Empfehlungen.
     * Basis: 2000 kcal Referenz-Erwachsener.
     */
    public double getFoodFactor() {
        return ENERGY_SERVICE.calculateDgeFoodFactor(this, LocalDate.now());
    }

    // Builder
    public static class Builder {
        private final HouseholdMember m = new HouseholdMember();

        public Builder(String name, Gender gender) {
            m.name = name;
            m.gender = gender;
            m.activityLevel = ActivityLevel.MODERATE;
            m.isActive = true;
        }

        public Builder birthYear(int v) { m.birthYear = v; return this; }
        public Builder weightKg(int v) { m.weightKg = v; return this; }
        public Builder heightCm(int v) { m.heightCm = v; return this; }
        public Builder targetWeightKg(int v) { m.targetWeightKg = v; return this; }
        public Builder activityLevel(ActivityLevel v) { m.activityLevel = v; return this; }
        public Builder active(boolean v) { m.isActive = v; return this; }

        public HouseholdMember build() { return m; }
    }
}
