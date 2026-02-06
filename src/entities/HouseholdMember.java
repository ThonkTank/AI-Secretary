package entities;

import java.time.LocalDate;
import java.time.Year;

/**
 * Repräsentiert ein Haushaltsmitglied für die Meal-Planning-Bedarfsberechnung.
 * Speichert Alter, Gewicht, Aktivität für BMR/TDEE-Berechnung.
 */
public class HouseholdMember {

    public Long id;
    public String name;                      // "Max", "Lisa"
    public int birthYear;                    // 1990
    public Gender gender;                    // MALE, FEMALE, OTHER
    public int weightKg;                     // 75
    public int heightCm;                     // 180
    public ActivityLevel activityLevel;      // SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE
    public boolean isActive;                 // true = wird bei Berechnung berücksichtigt

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

    // Builder
    public static class Builder {
        private final HouseholdMember member = new HouseholdMember();

        public Builder(String name) {
            member.name = name;
            member.isActive = true;
            member.activityLevel = ActivityLevel.MODERATE;
            member.gender = Gender.OTHER;
        }

        public Builder birthYear(int v) { member.birthYear = v; return this; }
        public Builder gender(Gender v) { member.gender = v; return this; }
        public Builder weightKg(int v) { member.weightKg = v; return this; }
        public Builder heightCm(int v) { member.heightCm = v; return this; }
        public Builder activityLevel(ActivityLevel v) { member.activityLevel = v; return this; }
        public Builder isActive(boolean v) { member.isActive = v; return this; }

        public HouseholdMember build() { return member; }
    }

    // ============== UTILITY ==============

    /**
     * Berechnet das aktuelle Alter.
     */
    public int getAge() {
        return Year.now().getValue() - birthYear;
    }

    /**
     * Berechnet BMR (Basal Metabolic Rate) via Mifflin-St Jeor Formel.
     * Männer: 10 × Gewicht(kg) + 6.25 × Größe(cm) − 5 × Alter + 5
     * Frauen: 10 × Gewicht(kg) + 6.25 × Größe(cm) − 5 × Alter − 161
     * @return BMR in kcal/Tag
     */
    public int calculateBMR() {
        int age = getAge();
        double bmr;
        if (gender == Gender.MALE) {
            bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + 5;
        } else {
            // FEMALE und OTHER verwenden weibliche Formel (konservativer)
            bmr = 10 * weightKg + 6.25 * heightCm - 5 * age - 161;
        }
        return (int) Math.round(bmr);
    }

    /**
     * Berechnet TDEE (Total Daily Energy Expenditure).
     * TDEE = BMR × Aktivitätsfaktor
     * @return TDEE in kcal/Tag
     */
    public int calculateTDEE() {
        return (int) Math.round(calculateBMR() * activityLevel.factor);
    }

    /**
     * Berechnet den Altersfaktor für Lebensmittelgruppen-Skalierung (DGE-basiert).
     */
    public double getAgeFactor() {
        int age = getAge();
        if (age < 4) return 0.3;
        if (age < 7) return 0.5;
        if (age < 10) return 0.7;
        if (age < 14) return 0.85;
        if (age < 19) return 1.1;  // Teenager brauchen mehr
        if (age > 65) return 0.85;
        return 1.0;
    }

    /**
     * Berechnet den Aktivitätsfaktor für Lebensmittelgruppen-Skalierung.
     */
    public double getActivityFoodFactor() {
        return switch (activityLevel) {
            case SEDENTARY -> 0.9;
            case LIGHT -> 1.0;
            case MODERATE -> 1.1;
            case ACTIVE -> 1.25;
            case VERY_ACTIVE -> 1.4;
        };
    }

    /**
     * Kombinierter Faktor für Lebensmittelgruppen-Berechnung.
     */
    public double getFoodFactor() {
        return getAgeFactor() * getActivityFoodFactor();
    }
}
