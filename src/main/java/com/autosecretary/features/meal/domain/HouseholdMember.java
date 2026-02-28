package com.autosecretary.features.meal.domain;

/**
 * A household member used for DGE energy-requirement calculations and meal planning.
 */
public class HouseholdMember {

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

    /**
     * Physical Activity Level (PAL) categories used to convert BMR to TDEE.
     * The {@code factor} values are standard PAL multipliers from nutritional science —
     * they are fixed reference values, not calibrated per user.
     * Reference: <a href="https://en.wikipedia.org/wiki/Physical_activity_level">Wikipedia — Physical activity level</a>.
     */
    public enum ActivityLevel {
        SEDENTARY(1.2, "Sitzend"),
        LIGHT(1.375, "Leicht aktiv"),
        MODERATE(1.55, "Moderat aktiv"),
        ACTIVE(1.725, "Aktiv"),
        VERY_ACTIVE(1.9, "Sehr aktiv");

        /** Standard PAL multiplier for this activity level (BMR × factor = TDEE). */
        public final double factor;
        public final String label;

        ActivityLevel(double factor, String label) {
            this.factor = factor;
            this.label = label;
        }
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
